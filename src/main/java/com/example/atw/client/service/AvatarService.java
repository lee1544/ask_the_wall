package com.example.atw.client.service;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.AvatarJobDto;
import com.example.atw.client.dto.AvatarRequest;
import com.example.atw.client.entity.ClientImageJob;
import com.example.atw.client.entity.ClientMedia;
import com.example.atw.client.entity.ClientRequestRecord;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.provider.BailianClient;
import com.example.atw.client.repository.ClientImageJobRepository;
import com.example.atw.client.support.ClientHashes;
import com.example.atw.client.support.ClientTime;
import com.example.atw.client.support.ImageUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private ClientRequestService requestService;

    @Autowired
    private BailianClient bailianClient;

    @Autowired
    private ClientMediaService mediaService;

    @Autowired
    private ClientImageJobRepository jobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("clientTaskExecutor")
    private Executor executor;

    public AvatarOutcome create(Integer userId, AvatarRequest request, String requestId) {
        if (request == null) {
            throw ClientApiException.invalid("请求体不能为空");
        }
        request.rejectOverrides(requestId);
        String prompt = request.getPrompt() == null ? "" : request.getPrompt().trim();
        if (prompt.isEmpty()) {
            throw ClientApiException.invalid("prompt 不能为空");
        }
        if (prompt.length() > properties.getImage().getMaxPromptChars()) {
            throw ClientApiException.invalid("prompt 最多 " + properties.getImage().getMaxPromptChars() + " 字符");
        }
        String reference = request.getImage();
        if (reference != null && !reference.isEmpty()) {
            ImageUtil.decodeJpegDataUrl(reference, properties.getImage().getMaxReferenceBytes());
        } else {
            reference = null;
        }

        String hash = ClientHashes.sha256(prompt + "|" + (reference == null ? "" : reference.length() + ":"
                + ClientHashes.sha256(reference)));
        ClientRequestService.Begin begin = requestService.begin(
                userId, requestId, ClientRequestService.OP_AVATAR_GENERATE, hash, null, null);
        if (begin.isReplay()) {
            return replay(begin.getRecord());
        }
        ClientRequestRecord record = begin.getRecord();

        ClientImageJob job = new ClientImageJob();
        job.setId("job-" + UUID.randomUUID().toString().replace("-", ""));
        job.setUserId(userId);
        job.setRequestId(requestId);
        job.setState(ClientImageJob.STATE_QUEUED);
        job.setPrompt(prompt);
        job.setReferenceImage(reference);
        job.setMessage("等待生成");
        job.setCreatedAt(ClientTime.now());
        job.setUpdatedAt(job.getCreatedAt());
        job.setExpiresAt(ClientTime.expireAfterHours(properties.getRequests().getRetentionHours()));
        try {
            jobRepository.save(job);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("job", toDto(job));
            requestService.complete(record, 202, body);
            return new AvatarOutcome(body, job.getId(), true, 202);
        } catch (RuntimeException e) {
            requestService.fail(record, 500, ClientCodes.INTERNAL_ERROR, "头像任务创建失败");
            throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "头像任务创建失败", requestId);
        }
    }

    public void submit(String jobId) {
        try {
            executor.execute(() -> run(jobId));
        } catch (RuntimeException e) {
            log.warn("头像任务提交失败 - jobId={}", jobId, e);
            failJob(jobId, ClientCodes.SERVICE_UNAVAILABLE, "服务繁忙，请稍后重试。");
        }
    }

    public AvatarJobDto get(Integer userId, String id) {
        ClientImageJob job = jobRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ClientApiException(404, ClientCodes.JOB_NOT_FOUND, "头像任务不存在"));
        if (ClientTime.isExpired(job.getExpiresAt())) {
            throw new ClientApiException(410, ClientCodes.REQUEST_EXPIRED, "头像任务已过期");
        }
        return toDto(job);
    }

    public AvatarJobDto cancel(Integer userId, String id) {
        ClientImageJob job = jobRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ClientApiException(404, ClientCodes.JOB_NOT_FOUND, "头像任务不存在"));
        if (ClientTime.isExpired(job.getExpiresAt())) {
            throw new ClientApiException(410, ClientCodes.REQUEST_EXPIRED, "头像任务已过期");
        }
        jobRepository.transition(id, activeStates(), ClientImageJob.STATE_CANCELLED, "已取消",
                null, null, null,
                ClientTime.expireAfterHours(properties.getRequests().getRetentionHours()),
                ClientTime.now());
        return toDto(jobRepository.findByIdAndUserId(id, userId).orElse(job));
    }

    public void purgeExpired() {
        List<ClientImageJob> expired = jobRepository.findByExpiresAtBefore(ClientTime.now());
        for (ClientImageJob job : expired) {
            jobRepository.delete(job);
        }
    }

    private void run(String jobId) {
        ClientImageJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !ClientImageJob.STATE_QUEUED.equals(job.getState())) {
            return;
        }
        int started = jobRepository.transition(jobId, Collections.singletonList(ClientImageJob.STATE_QUEUED),
                ClientImageJob.STATE_RUNNING, "正在生成", null, null, null,
                job.getExpiresAt(), ClientTime.now());
        if (started == 0) {
            return;
        }

        try {
            byte[] jpeg = job.getReferenceImage() == null ? null
                    : ImageUtil.decodeJpegDataUrl(job.getReferenceImage(),
                    properties.getImage().getMaxReferenceBytes());
            String text = buildPrompt(job.getPrompt(), jpeg != null);
            long deadline = System.currentTimeMillis() + properties.getImage().getReadTimeoutMs();
            String imageUrl = bailianClient.requestImage(text, jpeg);

            ClientImageJob current = jobRepository.findById(jobId).orElse(null);
            if (current == null || ClientImageJob.STATE_CANCELLED.equals(current.getState())) {
                return;
            }
            int downloading = jobRepository.transition(jobId,
                    Collections.singletonList(ClientImageJob.STATE_RUNNING),
                    ClientImageJob.STATE_DOWNLOADING, "正在下载", null, null, null,
                    current.getExpiresAt(), ClientTime.now());
            if (downloading == 0) {
                return;
            }

            long remaining = deadline - System.currentTimeMillis();
            int downloadTimeout = (int) Math.max(1L, Math.min(
                    Integer.MAX_VALUE,
                    Math.min(properties.getImage().getDownloadTimeoutMs(), remaining)));
            byte[] png = bailianClient.downloadOss(imageUrl,
                    properties.getImage().getMaxResultBytes(),
                    downloadTimeout);

            current = jobRepository.findById(jobId).orElse(null);
            if (current == null || ClientImageJob.STATE_CANCELLED.equals(current.getState())) {
                return;
            }
            ImageUtil.validatePng(png, properties.getImage().getMaxResultBytes(),
                    properties.getImage().getMaxSide(), properties.getImage().getMaxPixels());
            ClientMedia media = mediaService.save(job.getUserId(), ClientMediaService.TYPE_IMAGE,
                    "image/png", png, null);

            jobRepository.transition(jobId,
                    Arrays.asList(ClientImageJob.STATE_RUNNING, ClientImageJob.STATE_DOWNLOADING),
                    ClientImageJob.STATE_COMPLETE, "形象已生成，请预览并确认使用",
                    media.getId(), null, null, media.getExpiresAt(), ClientTime.now());
        } catch (ClientApiException e) {
            failJob(jobId, e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("头像任务执行失败 - jobId={}", jobId, e);
            failJob(jobId, ClientCodes.UPSTREAM_ERROR, "形象生成失败，请重试。");
        }
    }

    private void failJob(String jobId, String code, String message) {
        jobRepository.transition(jobId, activeStates(), ClientImageJob.STATE_ERROR, message,
                null, message, code,
                ClientTime.expireAfterHours(properties.getRequests().getRetentionHours()),
                ClientTime.now());
    }

    private List<String> activeStates() {
        return Arrays.asList(ClientImageJob.STATE_QUEUED, ClientImageJob.STATE_RUNNING,
                ClientImageJob.STATE_DOWNLOADING);
    }

    private String buildPrompt(String prompt, boolean hasReference) {
        String prefix = "生成用于页面头像展示的静态人物肖像，头肩构图，主体居中，背景简洁，不添加文字。";
        if (hasReference) {
            prefix += "根据参考图片保留人物主要特征。";
        }
        return prefix + "\n" + prompt;
    }

    private AvatarJobDto toDto(ClientImageJob job) {
        AvatarJobDto dto = new AvatarJobDto();
        dto.setId(job.getId());
        dto.setState(job.getState());
        dto.setMessage(job.getMessage() == null ? defaultMessage(job.getState()) : job.getMessage());
        dto.setCreatedAt(ClientTime.iso(job.getCreatedAt()));
        if (job.getMediaId() != null) {
            dto.setUrl(mediaService.url(job.getMediaId()));
        }
        if (isTerminal(job.getState())) {
            dto.setExpiresAt(ClientTime.iso(job.getExpiresAt()));
        }
        if (ClientImageJob.STATE_ERROR.equals(job.getState())) {
            dto.setError(job.getError());
            dto.setCode(job.getCode());
        }
        if (!isTerminal(job.getState())) {
            dto.setPollAfterMs(1000);
        }
        return dto;
    }

    private boolean isTerminal(String state) {
        return ClientImageJob.STATE_COMPLETE.equals(state)
                || ClientImageJob.STATE_ERROR.equals(state)
                || ClientImageJob.STATE_CANCELLED.equals(state);
    }

    private String defaultMessage(String state) {
        if (ClientImageJob.STATE_QUEUED.equals(state)) {
            return "等待生成";
        }
        if (ClientImageJob.STATE_RUNNING.equals(state)) {
            return "正在生成";
        }
        if (ClientImageJob.STATE_DOWNLOADING.equals(state)) {
            return "正在下载";
        }
        if (ClientImageJob.STATE_COMPLETE.equals(state)) {
            return "形象已生成，请预览并确认使用";
        }
        if (ClientImageJob.STATE_CANCELLED.equals(state)) {
            return "已取消";
        }
        return "形象生成失败，请重试。";
    }

    private AvatarOutcome replay(ClientRequestRecord record) {
        if (ClientRequestRecord.STATE_COMPLETED.equals(record.getState())) {
            if (record.getResultJson() == null) {
                throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "恢复结果为空");
            }
            try {
                JsonNode node = objectMapper.readTree(record.getResultJson());
                int status = record.getHttpStatus() == null ? 202 : record.getHttpStatus();
                return new AvatarOutcome(node, null, false, status);
            } catch (JsonProcessingException e) {
                throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "恢复结果解析失败");
            }
        }
        if (ClientRequestRecord.STATE_FAILED.equals(record.getState())) {
            throw new ClientApiException(record.getHttpStatus() == null ? 503 : record.getHttpStatus(),
                    record.getCode() == null ? ClientCodes.UPSTREAM_ERROR : record.getCode(),
                    record.getError() == null ? "头像任务创建失败" : record.getError(),
                    record.getRequestId());
        }
        throw new ClientApiException(409, ClientCodes.REQUEST_CANCELLED, "该请求已停止", record.getRequestId());
    }

    public static class AvatarOutcome {
        private final Object body;
        private final String jobId;
        private final boolean newJob;
        private final int status;

        public AvatarOutcome(Object body, String jobId, boolean newJob, int status) {
            this.body = body;
            this.jobId = jobId;
            this.newJob = newJob;
            this.status = status;
        }

        public Object getBody() {
            return body;
        }

        public String getJobId() {
            return jobId;
        }

        public boolean isNewJob() {
            return newJob;
        }

        public int getStatus() {
            return status;
        }
    }
}

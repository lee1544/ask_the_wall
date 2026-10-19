package com.example.atw.client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ClientCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ClientCleanupTask.class);

    @Autowired
    private ClientRequestService requestService;

    @Autowired
    private ClientMediaService mediaService;

    @Autowired
    private AvatarService avatarService;

    @Scheduled(initialDelay = 60000, fixedDelay = 600000)
    public void cleanup() {
        try {
            requestService.purgeExpired();
        } catch (Exception e) {
            log.warn("清理过期请求记录失败", e);
        }
        try {
            mediaService.purgeExpired();
        } catch (Exception e) {
            log.warn("清理过期媒体失败", e);
        }
        try {
            avatarService.purgeExpired();
        } catch (Exception e) {
            log.warn("清理过期头像任务失败", e);
        }
    }
}

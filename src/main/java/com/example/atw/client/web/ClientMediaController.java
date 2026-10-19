package com.example.atw.client.web;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.service.ClientMediaService;
import com.example.atw.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/client")
public class ClientMediaController {

    @Autowired
    private ClientMediaService mediaService;

    @GetMapping("/media/{id}")
    public ResponseEntity<?> download(@PathVariable String id,
                                      @RequestHeader(value = "Range", required = false) String range) {
        Integer userId = UserContextUtil.getCurrentUserId();
        ClientMediaService.MediaFile file = mediaService.open(userId, id);
        long size = file.size();
        MediaType mediaType = parseMediaType(file.getMedia().getContentType());

        if (range != null && !range.trim().isEmpty()) {
            return rangeResponse(file.getPath(), range, size, mediaType);
        }
        return ResponseEntity.ok()
                .header("Accept-Ranges", "bytes")
                .header("Cache-Control", "no-store")
                .header("Content-Disposition", "inline")
                .contentType(mediaType)
                .contentLength(size)
                .body(new FileSystemResource(file.getPath()));
    }

    private ResponseEntity<?> rangeResponse(Path path, String range, long size, MediaType mediaType) {
        String spec = range.trim();
        if (!spec.startsWith("bytes=")) {
            return rangeNotSatisfiable(size);
        }
        spec = spec.substring(6);
        int comma = spec.indexOf(',');
        if (comma >= 0) {
            spec = spec.substring(0, comma);
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return rangeNotSatisfiable(size);
        }
        long start;
        long end;
        try {
            String left = spec.substring(0, dash).trim();
            String right = spec.substring(dash + 1).trim();
            if (left.isEmpty()) {
                long suffix = Long.parseLong(right);
                if (suffix <= 0) {
                    return rangeNotSatisfiable(size);
                }
                start = Math.max(0, size - suffix);
                end = size - 1;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? size - 1 : Long.parseLong(right);
            }
        } catch (NumberFormatException e) {
            return rangeNotSatisfiable(size);
        }
        if (size <= 0 || start < 0 || start >= size || end < start) {
            return rangeNotSatisfiable(size);
        }
        end = Math.min(end, size - 1);
        long length = end - start + 1;
        if (length > Integer.MAX_VALUE) {
            return rangeNotSatisfiable(size);
        }
        byte[] data = readRange(path, start, (int) length);
        return ResponseEntity.status(206)
                .header("Content-Range", "bytes " + start + "-" + end + "/" + size)
                .header("Accept-Ranges", "bytes")
                .header("Cache-Control", "no-store")
                .header("Content-Disposition", "inline")
                .contentType(mediaType)
                .contentLength(length)
                .body(new ByteArrayResource(data));
    }

    private ResponseEntity<?> rangeNotSatisfiable(long size) {
        return ResponseEntity.status(416)
                .header("Content-Range", "bytes */" + size)
                .header("Cache-Control", "no-store")
                .build();
    }

    private byte[] readRange(Path path, long start, int length) {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(start);
            byte[] data = new byte[length];
            file.readFully(data);
            return data;
        } catch (IOException e) {
            throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "读取媒体文件失败");
        }
    }

    private MediaType parseMediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

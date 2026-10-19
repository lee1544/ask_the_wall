package com.example.atw.client.provider;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 基于 JDK HttpURLConnection 的轻量 HTTP 客户端（项目环境无 WebFlux）。
 */
@Component
public class HttpJsonClient {

    private static final int MAX_ERROR_BYTES = 64 * 1024;

    @Autowired
    private ObjectMapper objectMapper;

    public JsonResult postJson(String url, Map<String, String> headers, Object body,
                               int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection connection = null;
        try {
            byte[] payload = objectMapper.writeValueAsBytes(body);
            connection = open(url, headers, payload, connectTimeoutMs, readTimeoutMs,
                    "application/json");
            int status = connection.getResponseCode();
            if (status >= 400) {
                String error = readError(connection);
                return new JsonResult(status, error);
            }
            byte[] data = readAll(connection.getInputStream(), 16 * 1024 * 1024);
            return new JsonResult(status, new String(data, StandardCharsets.UTF_8));
        } catch (SocketTimeoutException e) {
            throw new ClientApiException(504, ClientCodes.UPSTREAM_TIMEOUT, "模型服务响应超时");
        } catch (IOException e) {
            throw new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, "模型服务连接失败");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public StreamResult postStream(String url, Map<String, String> headers, Object body,
                                   int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection connection = null;
        try {
            byte[] payload = objectMapper.writeValueAsBytes(body);
            connection = open(url, headers, payload, connectTimeoutMs, readTimeoutMs,
                    "text/event-stream");
            int status = connection.getResponseCode();
            if (status >= 400) {
                String error = readError(connection);
                connection.disconnect();
                return StreamResult.failed(status, error);
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            return StreamResult.ok(status, connection, reader);
        } catch (SocketTimeoutException e) {
            if (connection != null) {
                connection.disconnect();
            }
            throw new ClientApiException(504, ClientCodes.UPSTREAM_TIMEOUT, "模型服务响应超时");
        } catch (IOException e) {
            if (connection != null) {
                connection.disconnect();
            }
            throw new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, "模型服务连接失败");
        }
    }

    public DownloadResult download(String url, int maxBytes, int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection connection = null;
        try {
            URL target = new URL(url);
            connection = (HttpURLConnection) target.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept-Encoding", "identity");
            int status = connection.getResponseCode();
            if (status >= 300) {
                return new DownloadResult(status, null, connection.getContentType());
            }
            String contentType = connection.getContentType();
            byte[] data = readAll(connection.getInputStream(), maxBytes);
            return new DownloadResult(status, data, contentType);
        } catch (SocketTimeoutException e) {
            throw new ClientApiException(504, ClientCodes.UPSTREAM_TIMEOUT, "模型结果下载超时");
        } catch (IOException e) {
            throw new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, "模型结果下载失败");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection open(String url, Map<String, String> headers, byte[] payload,
                                   int connectTimeoutMs, int readTimeoutMs, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
            output.flush();
        }
        return connection;
    }

    private String readError(HttpURLConnection connection) {
        try {
            InputStream error = connection.getErrorStream();
            if (error == null) {
                return "";
            }
            return new String(readAll(error, MAX_ERROR_BYTES), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private byte[] readAll(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        try {
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("响应体超过上限");
                }
                output.write(buffer, 0, read);
            }
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
        return output.toByteArray();
    }

    public static class JsonResult {
        private final int status;
        private final String body;

        public JsonResult(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public int getStatus() {
            return status;
        }

        public String getBody() {
            return body;
        }
    }

    public static class DownloadResult {
        private final int status;
        private final byte[] body;
        private final String contentType;

        public DownloadResult(int status, byte[] body, String contentType) {
            this.status = status;
            this.body = body;
            this.contentType = contentType;
        }

        public int getStatus() {
            return status;
        }

        public byte[] getBody() {
            return body;
        }

        public String getContentType() {
            return contentType;
        }
    }

    public static class StreamResult implements Closeable {
        private final int status;
        private final String errorBody;
        private final HttpURLConnection connection;
        private final BufferedReader reader;

        private StreamResult(int status, String errorBody, HttpURLConnection connection, BufferedReader reader) {
            this.status = status;
            this.errorBody = errorBody;
            this.connection = connection;
            this.reader = reader;
        }

        public static StreamResult ok(int status, HttpURLConnection connection, BufferedReader reader) {
            return new StreamResult(status, null, connection, reader);
        }

        public static StreamResult failed(int status, String errorBody) {
            return new StreamResult(status, errorBody, null, null);
        }

        public boolean isSuccess() {
            return reader != null;
        }

        public int getStatus() {
            return status;
        }

        public String getErrorBody() {
            return errorBody;
        }

        public BufferedReader getReader() {
            return reader;
        }

        public HttpURLConnection getConnection() {
            return connection;
        }

        @Override
        public void close() {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}

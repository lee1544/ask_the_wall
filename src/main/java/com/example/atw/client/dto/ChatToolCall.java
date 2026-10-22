package com.example.atw.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatToolCall {

    private String id;
    private String type = "function";
    private Function function;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        private String name;
        private String arguments;
    }
}

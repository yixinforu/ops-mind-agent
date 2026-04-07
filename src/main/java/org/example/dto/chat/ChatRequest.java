package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 聊天请求
 */
@Data
public class ChatRequest {
    @JsonProperty(value = "Id")
    @JsonAlias({"id", "ID"})
    private String id;

    @JsonProperty(value = "Question")
    @JsonAlias({"question", "QUESTION"})
    private String question;
}

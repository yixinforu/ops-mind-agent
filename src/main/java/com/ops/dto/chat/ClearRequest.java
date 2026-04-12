package com.ops.dto.chat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 清空会话请求
 */
@Data
public class ClearRequest {
    @JsonProperty(value = "Id")
    @JsonAlias({"id", "ID"})
    private String id;
}

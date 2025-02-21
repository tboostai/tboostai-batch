package com.tboostai_batch.entity.OpenAI;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Data
@ToString
public class OpenAIRequest implements Serializable {

    private String model;

    @JsonProperty("messages")
    private List<Message> messages;

    // Parameter to control randomness of the model's response
    private Double temperature;

    // Parameter to control maximum length of response
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    // Parameter to control how much the model should prioritize diversity (penalize repeating phrases)
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    // Parameter to control how much the model should prioritize new words/phrases over used ones
    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    // Format of the response, can be set as "text" or "json"
    @JsonProperty("response_format")
    private String responseFormat;

    // Parameter to control whether OpenAI should stop after encountering certain tokens
    @JsonProperty("stop")
    private List<String> stop;

}

package com.tboostai_batch.service;

import com.tboostai_batch.entity.OpenAI.Message;
import com.tboostai_batch.entity.OpenAI.OpenAIRequest;
import com.tboostai_batch.entity.inner_model.FormattedDescription;
import com.tboostai_batch.util.CommonTools;
import com.tboostai_batch.util.WebClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.tboostai_batch.common.GeneralConstants.*;

@Service
public class VehicleDescriptionService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleDescriptionService.class);
    private final WebClientUtils webClientUtils;
    @Value("${openai.project.key}")
    private String openAIAPIKey;

    @Value("${openai.project.chat.url}")
    private String openAIAPIChatUrl;

    public VehicleDescriptionService(WebClientUtils webClientUtils) {
        this.webClientUtils = webClientUtils;
    }

    public FormattedDescription generateDescription(String originalDescriptionText) {
        if (originalDescriptionText == null || originalDescriptionText.isEmpty()) {
            return null;
        }
        String descObj = CommonTools.getDescriptionFromHTML(originalDescriptionText);
        logger.info("Description Object is {}", descObj);
        Map<String, String> request = new HashMap<>();
        request.put("description", descObj);
        Mono<FormattedDescription> responseResStr = this.beautifulDescriptions(request);
        logger.info("OpenAI Response is {}", responseResStr);
        FormattedDescription respFormattedDesc = responseResStr.block();
        logger.info("Response from llm service : {}", respFormattedDesc);
        return respFormattedDesc;
    }

    private Mono<FormattedDescription> beautifulDescriptions(Object description) {
        Map<String, String> requestHeaders = CommonTools.generateOpenAIRequestHeader(openAIAPIKey);
        logger.info("Request header is {}", requestHeaders);
        Message systemMsg = new Message();
        systemMsg.setRole(OPENAI_SYSTEM);
        systemMsg.setContent(OPENAI_SYSTEM_DEFAULT_MSG_FOR_BEAUTIFUL_DESC);

        Message descMsg = new Message();
        descMsg.setRole(OPENAI_USER);
        descMsg.setContent(description.toString());

        OpenAIRequest openAIRequest = new OpenAIRequest();
        List<Message> messages = new ArrayList<>();
        messages.add(systemMsg);
        messages.add(descMsg);
        openAIRequest.setMessages(messages);

        logger.info("OpenAI Request in class {} is {}", this.getClass().getName(), openAIRequest);

        String requestBody = CommonTools.parseObjToString(CommonTools.buildOpenAIRequestBody(openAIRequest));

        Mono<String> responseResStr = webClientUtils.sendExternalPostRequest(openAIAPIChatUrl, requestBody, requestHeaders, String.class, 3, 5);
        logger.info("OpenAI response in class {} is {}", this.getClass().getName(), responseResStr);

        return responseResStr.mapNotNull(response -> CommonTools.parseJsonToObject(response, FormattedDescription.class));
    }
}

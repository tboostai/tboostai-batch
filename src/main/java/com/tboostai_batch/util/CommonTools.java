package com.tboostai_batch.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tboostai_batch.entity.OpenAI.Message;
import com.tboostai_batch.entity.OpenAI.OpenAIRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

import static com.tboostai_batch.common.GeneralConstants.*;

public class CommonTools {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(CommonTools.class);

    public static HttpHeaders generateEbayBasicHeaders(String accessToken) {

        HttpHeaders headers = new HttpHeaders();
        headers.add(AUTHORIZATION, BEARER.concat(accessToken));
        headers.add(EBAY_HEADER_MARKET_PLACE, EBAY_CANADA);
        headers.add(ACCEPT_LANG, EN_CA);

        return headers;
    }

    // 自定义字段转换：字符串转为 Integer
    public static Integer parseInteger(String value) {
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // 自定义字段转换：字符串转为 BigDecimal
    public static BigDecimal parseBigDecimal(String value) {
        if (value != null) {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static String getDescriptionFromHTML(String originalText) {
        Map<String, List<String>> resultMap = parseHTML(originalText);
        String jsonStr = objToJsonStr(resultMap);

        return Objects.requireNonNullElse(jsonStr, String.valueOf(resultMap));
    }

    public static Map<String, List<String>> parseHTML(String html) {

        Map<String, List<String>> resultMap = new HashMap<>();
        Set<String> memorySet = new HashSet<>();
        List<String> exactMatchList = new ArrayList<>();
        List<String> fuzzyMatchList = new ArrayList<>();
        // 解析HTML
        Document doc = Jsoup.parse(html);

        // 提取包含 "description" 关键字的 meta 标签内容
        Elements metaDescriptions = doc.select("meta[name~=description], meta[property~=description]");
        for (Element metaDesc : metaDescriptions) {
            String currentAttribute = metaDesc.attr("content");
            if (!memorySet.contains(currentAttribute)) {
                exactMatchList.add(metaDesc.attr("content"));
                memorySet.add(metaDesc.attr("content"));
                logger.info("Description from meta: {}", metaDesc.attr("content"));
            }
        }

        // 提取id或class中包含 'description' 或类似关键字的元素
        Elements potentialDescriptions = doc.select("[id*=description], [class*=description], [property*=description]");
        for (Element desc : potentialDescriptions) {
            String currentText = desc.text();
            if (!memorySet.contains(currentText)) {
                exactMatchList.add(currentText);
                memorySet.add(currentText);
                logger.info("Description from id, class or property: {}", desc.attr("content"));
            }
        }

        // 尝试通过标签名称、属性名等获取疑似description的元素
        Elements paragraphs = doc.select("p, div, span");
        for (Element para : paragraphs) {
            if (para.text().toLowerCase().contains("description") || para.id().toLowerCase().contains("description")) {
                String currentText = para.text();
                if (!memorySet.contains(currentText)) {
                    fuzzyMatchList.add(para.text());
                    memorySet.add(currentText);
                    logger.info("Looks like a description content from paragraph: {}", para.text());
                }
            }
        }

        if (exactMatchList.isEmpty() && fuzzyMatchList.isEmpty()) {
            String cleanedHtml = generateCleanBodyText(html);
            logger.info("No description extracted, cleaned HTML: {}", cleanedHtml);
            fuzzyMatchList.add(cleanedHtml);
        }

        resultMap.put("exactMatch", exactMatchList);
        resultMap.put("fuzzyMatch", fuzzyMatchList);

        return resultMap;
    }

    public static String generateCleanBodyText(String htmlContent) {
        // 解析HTML字符串
        Document document = Jsoup.parse(htmlContent);

        // 获取<body>标签内容
        Element body = document.body();

        // 去除图片、视频等媒体标签
        removeMediaTags(body);

        // 提取body中的文本内容
        return body.text();
    }

    // 删除所有图片、视频等媒体标签
    private static void removeMediaTags(Element element) {
        // 删除<img>, <video>, <audio>, <embed>, <iframe>等标签
        String[] mediaTags = {"img", "video", "audio", "embed", "iframe"};

        for (String tag : mediaTags) {
            Elements mediaElements = element.getElementsByTag(tag);
            mediaElements.remove();
        }
    }


    public static String objToJsonStr(Object object) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse map to JSON string", e);
            return null;
        }
    }

    public static <T> void setIfNotNull(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
    public static String mapListToString(List<String> stringList) {
        return (stringList != null && !stringList.isEmpty()) ? String.join(",", stringList) : null;
    }

    public static Map<String, String> generateOpenAIRequestHeader(String openAIAPIKey) {
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(CONTENT_TYPE, APPLICATION_JSON);
        requestHeaders.put(ACCEPT, APPLICATION_JSON);
        requestHeaders.put(AUTHORIZATION, openAIAPIKey);

        return requestHeaders;
    }

    public static Map<String, Object> buildOpenAIRequestBody(OpenAIRequest openAIRequest) {
        Map<String, Object> body = new HashMap<>();
        body.put(OPENAI_MODEL_KEY, OPENAI_MODEL_VALUE);

        List<Map<String, String>> messages = new ArrayList<>();

        for (Message message : openAIRequest.getMessages()) {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put(OPENAI_ROLE, message.getRole());
            messageMap.put(OPENAI_CONTENT, message.getContent());
            messages.add(messageMap);
        }

        body.put(OPENAI_MESSAGES, messages);

        return body;
    }

    public static String parseObjToString(Object obj) {

        String jsonBody = "";
        try {
            jsonBody = objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("Failed to build request body JSON", e);
        }

        logger.info("RequestBody is {}", jsonBody);

        return jsonBody;
    }

    public static <T> T parseJsonToObject(String jsonBody, Class<T> clazz) {
        logger.info("jsonBody is {}", jsonBody);
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonBody);
            String content = root.path(OPENAI_CHOICES).get(0).path(OPENAI_MESSAGE).path(OPENAI_CONTENT).asText();
            logger.info("Response content from OpenAI: {}", content);
            return objectMapper.readValue(content, clazz);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse json response", e);
        }
        return null;
    }

}

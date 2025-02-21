package com.tboostai_batch.common;

public class GeneralConstants {


    public static final int CONNECT_TIMEOUT = 60000;   // 60 秒
    public static final int RESPONSE_TIMEOUT = 60000; // 60 秒
    public static final int IO_TIMEOUT = 60000;       // 10 秒
    public static final long ONE_YEAR_IN_SECONDS = 31_536_000L;
    public static final String HTTPS = "https";
    public static final String CATEGORY_IDS = "category_ids";
    public static final String EN_CA = "en-CA";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer ";
    public static final String ACCEPT_LANG = "Accept-Language";
    public static final String STAR = "*";
    public static final int TIMEOUT_60_SECONDS = 60;

    // Ebay
    public static final String EBAY_CANADA = "EBAY_CA";
    public static final String EBAY_PLATFORM= "ebay";
    public static final String LIMIT = "limit";
    public static final String OFFSET = "offset";
    public static final String EBAY_HEADER_MARKET_PLACE = "X-EBAY-C-MARKETPLACE-ID";
    public static final String EBAY_SEARCH_API_LIMIT_CALL_MAX = "10";

    public static final int WEBCLIENT_BUFFER_SIZE = 1024 * 1024; //1MB

    public static final String GOOGLE_MAP_API_ADDR = "address";
    public static final String GOOGLE_MAP_API_KEY = "key";


    public static final String OPENAI_SYSTEM = "system";
    public static final String OPENAI_USER = "user";
    public static final String APPLICATION_JSON = "application/json";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String ACCEPT = "Accept";
    public static final String OPENAI_MODEL_KEY = "model";
    public static final String OPENAI_MODEL_VALUE = "gpt-4o";
    public static final String OPENAI_ROLE = "role";
    public static final String OPENAI_CONTENT = "content";
    public static final String OPENAI_MESSAGES = "messages";
    public static final String OPENAI_MESSAGE = "message";
    public static final String OPENAI_CHOICES = "choices";
    public static final String OPENAI_SYSTEM_DEFAULT_MSG_FOR_BEAUTIFUL_DESC =
            """
                    You are a professional writer with a strong ability to extract and summarize key information.
                    Extract the Vehicle's description from the given content and summarize it as concise bullet points,\s
                    with each point limited to around 20-30 characters to avoid excessive detail. Remove repeated details in bullet points,\s
                    like the same engine type mentioned multiple times. For extractedFeatures, focus on general configurations and common options,\s
                    and avoid duplicate or overly specific details tied to a single model.
                    Respond in valid, raw JSON format only, strictly following this structure without additional symbols or formatting:
                    {
                       "originalDescription": "This is the description of the item",
                       "summarized": ["description bullet point 1", "description bullet point 2"],
                       "extractedFeatures": ["sunroof", "GPS", "Apple CarPlay"]
                    }    \s
                    Ensure:
                    1. No backticks, quotes, or extra characters are added around the JSON structure.
                    2. JSON should be in valid raw format, ready for direct parsing.
            """;

}
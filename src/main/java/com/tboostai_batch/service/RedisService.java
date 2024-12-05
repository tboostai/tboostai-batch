package com.tboostai_batch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tboostai_batch.entity.ebay.dto.EbayRespBasicDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.tboostai_batch.common.GeneralConstants.ONE_YEAR_IN_SECONDS;

@Service
public class RedisService {
    private static final Logger logger = LoggerFactory.getLogger(RedisService.class);

    private static final String NEXT_URL_KEY = "next_url";  // Persistent key, cached until job ends
    private static final String PROCESSED_ITEMS_SET_KEY = "processed_items";  // Persistent key, cached until job ends
    private static final String CURRENT_BATCH_LIST_KEY = "current_batch_items";   // Temporary cache for the current batch
    private static final String TEMP_ITEM_DETAILS = "temp_item_details";   // Temporary cache for item details in the current batch

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Cache for temporarily storing data to be written to Redis later
    private final Map<String, String> redisCache = new HashMap<>();

    private final LettuceConnectionFactory lettuceConnectionFactory;

    @Autowired
    public RedisService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, LettuceConnectionFactory lettuceConnectionFactory) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.lettuceConnectionFactory = lettuceConnectionFactory;
    }

    // -------------------
    // Function 1: Store and retrieve nextURL (persistent, cached until the end of job)
    // -------------------

    // Save nextURL (cached during job execution, persisted at the end)
    public void setNextUrl(String nextUrl) {
        redisCache.put(NEXT_URL_KEY, nextUrl);  // Store nextUrl in cache
    }

    // Retrieve the current nextURL from Redis
    public String getNextUrl() {
        logger.info("RedisService - redis host is {} and redis port is {}", lettuceConnectionFactory.getHostName(), lettuceConnectionFactory.getPort());
        return redisTemplate.opsForValue().get(NEXT_URL_KEY);
    }

    // -------------------
    // Function 2: Store and retrieve processed item IDs to Set (persistent, cached until the end of job)
    // -------------------

    // Temporarily store processed itemIds in cache (persisted at the end of the job)
    public void storeProcessedItems(List<String> itemIds) {
        redisCache.put(PROCESSED_ITEMS_SET_KEY, String.join(",", itemIds));  // Store processed items in cache
    }

    // Retrieve all processed itemIds from Redis
    public List<String> getProcessedItems() {
        return new ArrayList<>(Objects.requireNonNull(redisTemplate.opsForSet().members(PROCESSED_ITEMS_SET_KEY)));
    }

    // -------------------
    // Function 3: Store and retrieve the current batch of item IDs (temporary storage)
    // -------------------

    // Store the current batch of itemIds directly in Redis (temporary cache)
    public void storeCurrentBatch(List<String> itemIds) {
        redisTemplate.delete(CURRENT_BATCH_LIST_KEY);  // Clear any previous batch
        redisTemplate.opsForList().rightPushAll(CURRENT_BATCH_LIST_KEY, itemIds);  // Store the new batch in Redis List
    }

    // Retrieve the current batch of itemIds from Redis
    public List<String> getCurrentBatch() {
        return redisTemplate.opsForList().range(CURRENT_BATCH_LIST_KEY, 0, -1);
    }

    // -------------------
    // Function 4: Store and retrieve item details (temporary storage)
    // -------------------

    // Temporarily store item details in Redis (temporary cache)
    public void storeItemDetailsInRedis(List<EbayRespBasicDTO> itemDetails) {
        redisTemplate.delete(TEMP_ITEM_DETAILS);  // Clear previous item details
        for (EbayRespBasicDTO item : itemDetails) {
            try {
                // Serialize EbayRespEntityBasic object as JSON string
                String jsonString = objectMapper.writeValueAsString(item);
                redisTemplate.opsForHash().put(TEMP_ITEM_DETAILS, item.getItemId(), jsonString);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize item details into JSON", e);
            }
        }
    }

    // Retrieve the current batch of item details from Redis
    public List<EbayRespBasicDTO> getItemDetailsFromRedis() {
        Map<Object, Object> items = redisTemplate.opsForHash().entries(TEMP_ITEM_DETAILS);
        if (items.isEmpty()) {
            logger.warn("No item details found in Redis");
            return List.of();  // Return an empty list to avoid null pointer exception
        }

        return items.values().stream()
                .map(jsonString -> {
                    try {
                        // Deserialize JSON string into EbayRespEntityBasic object
                        return objectMapper.readValue(jsonString.toString(), EbayRespBasicDTO.class);
                    } catch (JsonProcessingException e) {
                        logger.error("Failed to deserialize item details from JSON", e);
                        return null;  // Return null if deserialization fails
                    }
                })
                .filter(Objects::nonNull)  // Filter out null objects due to deserialization failure
                .collect(Collectors.toList());
    }

    // -------------------
    // Function 5: Persist cached data at the end of the job
    // -------------------

    // Persist cached data (NEXT_URL_KEY and PROCESSED_ITEMS_SET_KEY) to Redis
    public void persistCacheToRedis() {
        if (redisCache.containsKey(NEXT_URL_KEY)) {
            redisTemplate.opsForValue().set(NEXT_URL_KEY, redisCache.get(NEXT_URL_KEY), ONE_YEAR_IN_SECONDS, TimeUnit.SECONDS);  // Set 1-year expiration
        }

        if (redisCache.containsKey(PROCESSED_ITEMS_SET_KEY)) {
            String[] items = redisCache.get(PROCESSED_ITEMS_SET_KEY).split(",");
            redisTemplate.opsForSet().add(PROCESSED_ITEMS_SET_KEY, items);  // Persist processed items
            redisTemplate.expire(PROCESSED_ITEMS_SET_KEY, ONE_YEAR_IN_SECONDS, TimeUnit.SECONDS);  // Set 1-year expiration
        }

        redisCache.clear();  // Clear the cache after persisting to Redis
    }

    // -------------------
    // Function 6: Clear temporary data in Redis (to be called at the end of batch job)
    // -------------------

    // Clear temporarily stored item details from Redis
    public void clearItemDetailsFromRedis() {
        redisTemplate.delete(TEMP_ITEM_DETAILS);
    }

    // Clear the current batch itemId cache from Redis
    public void clearCurrentBatchList() {
        redisTemplate.delete(CURRENT_BATCH_LIST_KEY);  // Clear the CURRENT_BATCH_LIST_KEY
    }

    // Clear all temporary Redis data at the end of the batch job
    public void clearAllTempData() {
        clearItemDetailsFromRedis();
        clearCurrentBatchList();
    }
}

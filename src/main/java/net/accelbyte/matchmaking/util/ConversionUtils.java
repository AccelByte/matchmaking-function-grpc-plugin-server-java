// Copyright (c) 2023 AccelByte Inc. All Rights Reserved.
// This is licensed software from AccelByte Inc, for limitations
// and restrictions contact your company contract manager.

package net.accelbyte.matchmaking.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Utility class for converting attributes to JSON-compatible format.
 */
public class ConversionUtils {

    private static final Logger logger = LoggerFactory.getLogger(ConversionUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts attribute data to JSON-compatible format through a round-trip conversion.
     * This ensures all values are properly typed according to JSON standards and
     * removes any non-JSON-serializable values or structures.
     *
     * @param data The input attribute map to convert
     * @return The converted map with normalized data types
     */
    public static Map<String, Object> convertAttribute(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        try {
            // JSON round-trip conversion for normalization
            String json = objectMapper.writeValueAsString(data);
            Map<String, Object> result = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            logger.debug("Successfully converted attributes: {} -> {}", data, result);
            return result;

        } catch (Exception e) {
            logger.error("Failed to convert attributes: {} error: {}", data, e.getMessage());
            logger.error("Error on convertAttribute for attributes", e);

            // Return original data if conversion fails
            return data;
        }
    }

    /**
     * Converts a single attribute value to JSON-compatible format.
     *
     * @param value The input value to convert
     * @return The converted value
     */
    public static Object convertAttributeValue(Object value) {
        if (value == null) {
            return null;
        }

        try {
            // JSON round-trip conversion for single value
            String json = objectMapper.writeValueAsString(value);
            return objectMapper.readValue(json, Object.class);

        } catch (Exception e) {
            logger.error("Failed to convert attribute value: {} error: {}", value, e.getMessage());
            // Return original value if conversion fails
            return value;
        }
    }
}
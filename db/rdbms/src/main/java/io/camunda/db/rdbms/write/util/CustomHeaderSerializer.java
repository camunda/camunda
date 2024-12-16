/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomHeaderSerializer {

  private static final Logger LOG = LoggerFactory.getLogger(CustomHeaderSerializer.class);

  public static String serialize(final Map<String, String> headers) {
    if (headers == null) {
      return null;
    }

    final ObjectMapper mapper = new ObjectMapper();
    String serializedCustomHeaders = null;
    try {
      serializedCustomHeaders = mapper.writeValueAsString(headers);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize custom headers!", e);
    }

    return serializedCustomHeaders;
  }

  public static Map<String, String> deserialize(final String serializedCustomHeader) {
    final ObjectMapper mapper = new ObjectMapper();
    if (serializedCustomHeader == null || serializedCustomHeader.isEmpty()) {
      return null;
    }

    Map<String, String> customHeaders = null;
    try {
      customHeaders = mapper.readValue(serializedCustomHeader, Map.class);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to deserialize custom headers!", e);
    }

    return customHeaders;
  }
}

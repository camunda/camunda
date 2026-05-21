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

public class MapSerializer {

  private static final Logger LOG = LoggerFactory.getLogger(MapSerializer.class);

  public static String serialize(final Map<String, String> map) {
    if (map == null) {
      return null;
    }

    final ObjectMapper mapper = new ObjectMapper();
    String serialized = null;
    try {
      serialized = mapper.writeValueAsString(map);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize map!", e);
    }

    return serialized;
  }

  public static Map<String, String> deserialize(final String serialized) {
    final ObjectMapper mapper = new ObjectMapper();
    if (serialized == null || serialized.isEmpty()) {
      return null;
    }

    Map<String, String> map = null;
    try {
      map = mapper.readValue(serialized, Map.class);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to deserialize map!", e);
    }

    return map;
  }
}

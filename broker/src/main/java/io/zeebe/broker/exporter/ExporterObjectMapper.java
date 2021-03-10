/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class ExporterObjectMapper {

  private final ObjectMapper jsonObjectMapper;

  public ExporterObjectMapper() {
    final InjectableValues.Std injectableValues = new InjectableValues.Std();
    injectableValues.addValue(ExporterObjectMapper.class, this);

    jsonObjectMapper = createDefaultObjectMapper(injectableValues);
  }

  private ObjectMapper createDefaultObjectMapper(final InjectableValues injectableValues) {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    objectMapper.setInjectableValues(injectableValues);

    objectMapper.registerModule(new JavaTimeModule()); // to serialize INSTANT
    objectMapper.enable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
    objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);

    return objectMapper;
  }

  public String toJson(final Object value) {
    try {
      return jsonObjectMapper.writeValueAsString(value);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(
          String.format("Failed to serialize object '%s' to JSON", value), e);
    }
  }
}

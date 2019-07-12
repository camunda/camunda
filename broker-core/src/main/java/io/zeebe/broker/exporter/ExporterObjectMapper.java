/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class ExporterObjectMapper {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};
  private static final TypeReference<Map<String, Object>> STRING_MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};

  private final ObjectMapper msgpackObjectMapper;
  private final ObjectMapper jsonObjectMapper;

  public ExporterObjectMapper() {
    final InjectableValues.Std injectableValues = new InjectableValues.Std();
    injectableValues.addValue(ExporterObjectMapper.class, this);

    msgpackObjectMapper = createMsgpackObjectMapper(injectableValues);
    jsonObjectMapper = createDefaultObjectMapper(injectableValues);
  }

  private ObjectMapper createDefaultObjectMapper(InjectableValues injectableValues) {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    objectMapper.setInjectableValues(injectableValues);

    objectMapper.registerModule(new JavaTimeModule()); // to serialize INSTANT
    objectMapper.enable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
    objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);

    return objectMapper;
  }

  private ObjectMapper createMsgpackObjectMapper(InjectableValues injectableValues) {
    final MessagePackFactory msgpackFactory =
        new MessagePackFactory().setReuseResourceInGenerator(false).setReuseResourceInParser(false);
    final ObjectMapper objectMapper = new ObjectMapper(msgpackFactory);

    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    objectMapper.setInjectableValues(injectableValues);

    return objectMapper;
  }

  public String toJson(Object value) {
    try {
      return jsonObjectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          String.format("Failed to serialize object '%s' to JSON", value), e);
    }
  }

  public byte[] toMsgpack(Object value) {
    try {
      return msgpackObjectMapper.writeValueAsBytes(value);
    } catch (IOException e) {
      throw new RuntimeException(
          String.format("Failed to serialize object '%s' to Msgpack JSON", value), e);
    }
  }

  public Map<String, Object> fromJsonAsMap(String json) {
    try {
      return msgpackObjectMapper.readValue(json, MAP_TYPE_REFERENCE);
    } catch (IOException e) {
      throw new RuntimeException("Failed deserialize JSON to map", e);
    }
  }

  public Map<String, Object> fromMsgpackAsMap(InputStream inputStream) {
    try {
      return msgpackObjectMapper.readValue(inputStream, MAP_TYPE_REFERENCE);
    } catch (IOException e) {
      throw new RuntimeException("Failed deserialize Msgpack JSON to map", e);
    }
  }

  public Map<String, String> fromMsgpackAsStringMap(InputStream inputStream) {
    try {
      return msgpackObjectMapper.readValue(inputStream, STRING_MAP_TYPE_REFERENCE);
    } catch (IOException e) {
      throw new RuntimeException("Failed deserialize Msgpack JSON to map", e);
    }
  }
}

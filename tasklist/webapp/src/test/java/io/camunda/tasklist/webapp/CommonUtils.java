/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.tasklist.connect.ElasticsearchConnector;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

public final class CommonUtils {

  public static final ObjectMapper OBJECT_MAPPER = getObjectMapper();

  public static ObjectMapper getObjectMapper() {
    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    final DateTimeFormatter dateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
    javaTimeModule.addSerializer(
        OffsetDateTime.class,
        new ElasticsearchConnector.CustomOffsetDateTimeSerializer(dateTimeFormatter));
    javaTimeModule.addDeserializer(
        OffsetDateTime.class,
        new ElasticsearchConnector.CustomOffsetDateTimeDeserializer(dateTimeFormatter));
    javaTimeModule.addDeserializer(
        Instant.class, new ElasticsearchConnector.CustomInstantDeserializer());

    return Jackson2ObjectMapperBuilder.json()
        .modules(javaTimeModule, new Jdk8Module())
        .featuresToDisable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT)
        .build();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.db.search.engine.config.ConnectConfiguration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class JacksonConfiguration {

  private final ConnectConfiguration configuration;

  public JacksonConfiguration(final ConnectConfiguration configuration) {
    this.configuration = configuration;
  }

  public ObjectMapper createObjectMapper() {
    final var dateTimeFormatter = createDateTimeFormatter();
    final JavaTimeModule javaTimeModule = new JavaTimeModule();

    javaTimeModule.addSerializer(
        OffsetDateTime.class, new CustomOffsetDateTimeSerializer(dateTimeFormatter));
    javaTimeModule.addDeserializer(
        OffsetDateTime.class, new CustomOffsetDateTimeDeserializer(dateTimeFormatter));
    javaTimeModule.addDeserializer(Instant.class, new CustomInstantDeserializer());

    return new ObjectMapper()
        .registerModules(javaTimeModule, new Jdk8Module())
        // disable
        .configure(SerializationFeature.INDENT_OUTPUT, false)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
        // enable
        .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
        // make sure that Jackson uses setters and getters, not fields
        .setVisibility(PropertyAccessor.GETTER, Visibility.ANY)
        .setVisibility(PropertyAccessor.IS_GETTER, Visibility.ANY)
        .setVisibility(PropertyAccessor.SETTER, Visibility.ANY)
        .setVisibility(PropertyAccessor.FIELD, Visibility.NONE)
        .setVisibility(PropertyAccessor.CREATOR, Visibility.ANY);
  }

  public DateTimeFormatter createDateTimeFormatter() {
    return DateTimeFormatter.ofPattern(configuration.getDateFormat());
  }
}

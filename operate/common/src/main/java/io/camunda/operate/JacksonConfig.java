/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.operate.connect.CustomInstantDeserializer;
import io.camunda.operate.connect.CustomOffsetDateTimeDeserializer;
import io.camunda.operate.connect.CustomOffsetDateTimeSerializer;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

  @Bean("operateObjectMapperCustomizer")
  public Consumer<Jackson2ObjectMapperBuilder> operateObjectMapperCustomizer(
      final OperateDateTimeFormatter dateTimeFormatter) {

    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(
        OffsetDateTime.class,
        new CustomOffsetDateTimeSerializer(dateTimeFormatter.getGeneralDateTimeFormatter()));
    javaTimeModule.addDeserializer(
        OffsetDateTime.class,
        new CustomOffsetDateTimeDeserializer(dateTimeFormatter.getGeneralDateTimeFormatter()));
    javaTimeModule.addDeserializer(Instant.class, new CustomInstantDeserializer());

    return builder ->
        builder
            .modulesToInstall(modules -> modules.addAll(List.of(javaTimeModule, new Jdk8Module())))
            .featuresToDisable(
                SerializationFeature.INDENT_OUTPUT,
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS)
            // make sure that Jackson uses setters and getters, not fields
            .visibility(PropertyAccessor.GETTER, Visibility.ANY)
            .visibility(PropertyAccessor.IS_GETTER, Visibility.ANY)
            .visibility(PropertyAccessor.SETTER, Visibility.ANY)
            .visibility(PropertyAccessor.FIELD, Visibility.NONE)
            .visibility(PropertyAccessor.CREATOR, Visibility.ANY);
  }

  @Bean("operateObjectMapper")
  public ObjectMapper objectMapper(final OperateDateTimeFormatter dateTimeFormatter) {
    final var builder = Jackson2ObjectMapperBuilder.json();
    operateObjectMapperCustomizer(dateTimeFormatter).accept(builder);
    return builder.build();
  }

  // Some common components autowire the datetime formatter directly. To avoid potentially impacting
  // critical code or needing to refactor in multiple places, expose the general date time formatter
  // as
  // a bean just like it was before the introduction of the OperateDateTimeFormatter component
  @Bean
  public DateTimeFormatter dateTimeFormatter(final OperateDateTimeFormatter dateTimeFormatter) {
    return dateTimeFormatter.getGeneralDateTimeFormatter();
  }
}

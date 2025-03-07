/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.tasklist.connect.ElasticsearchConnector;
import io.camunda.tasklist.property.TasklistProperties;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration("tasklistJacksonConfig")
public class JacksonConfig {

  @Autowired private TasklistProperties tasklistProperties;

  @Bean("tasklistObjectMapperCustomizer")
  public Consumer<Jackson2ObjectMapperBuilder> tasklistObjectMapperCustomizer() {
    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(
        OffsetDateTime.class,
        new ElasticsearchConnector.CustomOffsetDateTimeSerializer(dateTimeFormatter()));
    javaTimeModule.addDeserializer(
        OffsetDateTime.class,
        new ElasticsearchConnector.CustomOffsetDateTimeDeserializer(dateTimeFormatter()));
    javaTimeModule.addDeserializer(
        Instant.class, new ElasticsearchConnector.CustomInstantDeserializer());

    //    javaTimeModule.addSerializer(LocalDate.class, new
    // ElasticsearchConnector.CustomLocalDateSerializer(localDateFormatter()));
    //    javaTimeModule.addDeserializer(LocalDate.class, new
    // ElasticsearchConnector.CustomLocalDateDeserializer(localDateFormatter()));

    return builder ->
        builder
            .modulesToInstall(modules -> modules.addAll(List.of(javaTimeModule, new Jdk8Module())))
            .featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT)
            .build();
  }

  @Bean("tasklistObjectMapper")
  public ObjectMapper objectMapper() {
    final var builder = Jackson2ObjectMapperBuilder.json();
    tasklistObjectMapperCustomizer().accept(builder);
    return builder.build();
  }

  private DateTimeFormatter dateTimeFormatter() {
    return DateTimeFormatter.ofPattern(tasklistProperties.getElasticsearch().getDateFormat());
  }

  private DateTimeFormatter localDateFormatter() {
    return DateTimeFormatter.ofPattern(tasklistProperties.getZeebeElasticsearch().getDateFormat());
  }
}

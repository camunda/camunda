/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.zeebe.tasklist.es.ElasticsearchConnector;
import io.zeebe.tasklist.property.TasklistProperties;

@Configuration
public class JacksonConfig {

  @Autowired
  private TasklistProperties tasklistProperties;

  @Bean
  public ObjectMapper objectMapper() {
    final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    getObjectMapperConfigurer().accept(objectMapper);
    return objectMapper;
  }

  @Bean
  public DateTimeFormatter dateTimeFormatter() {
    return DateTimeFormatter.ofPattern(tasklistProperties.getElasticsearch().getDateFormat());
  }

  @Bean
  public DateTimeFormatter localDateFormatter() {
    return DateTimeFormatter.ofPattern(tasklistProperties.getZeebeElasticsearch().getDateFormat());
  }

  @Bean("tasklistObjectMapperConfigurer")
  public Consumer<ObjectMapper> getObjectMapperConfigurer() {
    return (ObjectMapper mapper) -> {
      JavaTimeModule javaTimeModule = new JavaTimeModule();
      javaTimeModule.addSerializer(OffsetDateTime.class, new ElasticsearchConnector.CustomOffsetDateTimeSerializer(dateTimeFormatter()));
      javaTimeModule.addDeserializer(OffsetDateTime.class, new ElasticsearchConnector.CustomOffsetDateTimeDeserializer(dateTimeFormatter()));
      javaTimeModule.addDeserializer(Instant.class, new ElasticsearchConnector.CustomInstantDeserializer());

      //    javaTimeModule.addSerializer(LocalDate.class, new ElasticsearchConnector.CustomLocalDateSerializer(localDateFormatter()));
      //    javaTimeModule.addDeserializer(LocalDate.class, new ElasticsearchConnector.CustomLocalDateDeserializer(localDateFormatter()));

      mapper.registerModules(javaTimeModule, new Jdk8Module())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
              DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .enable(JsonParser.Feature.ALLOW_COMMENTS)
          .enable(SerializationFeature.INDENT_OUTPUT);
    };
  }

}

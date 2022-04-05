/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import io.camunda.operate.es.ElasticsearchConnector;
import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class JacksonConfig {

  @Autowired
  private OperateProperties operateProperties;

  @Bean("operateObjectMapper")
  public ObjectMapper objectMapper() {

    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new ElasticsearchConnector.CustomOffsetDateTimeSerializer(dateTimeFormatter()));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new ElasticsearchConnector.CustomOffsetDateTimeDeserializer(dateTimeFormatter()));
    javaTimeModule.addDeserializer(Instant.class, new ElasticsearchConnector.CustomInstantDeserializer());

    return Jackson2ObjectMapperBuilder.json()
        .modules(javaTimeModule, new Jdk8Module())
        .featuresToDisable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT)
        //make sure that Jackson uses setters and getters, not fields
        .visibility(PropertyAccessor.GETTER, Visibility.ANY)
        .visibility(PropertyAccessor.IS_GETTER, Visibility.ANY)
        .visibility(PropertyAccessor.SETTER, Visibility.ANY)
        .visibility(PropertyAccessor.FIELD, Visibility.NONE)
        .visibility(PropertyAccessor.CREATOR, Visibility.NONE)
        .build();
  }

  @Bean
  public DateTimeFormatter dateTimeFormatter() {
    return DateTimeFormatter.ofPattern(operateProperties.getElasticsearch().getDateFormat());
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class GatewayObjectMapper {

  private static ObjectMapper strictIntegerMapper;

  private ObjectMapper strictIntegerObjectMapper() {
    if (strictIntegerMapper == null) {
      strictIntegerMapper =
          Jackson2ObjectMapperBuilder.json()
              .featuresToDisable(
                  DeserializationFeature.ACCEPT_FLOAT_AS_INT,
                  SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                  DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
                  DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                  DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
              .featuresToEnable(
                  JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT)
              .build();
    }
    return strictIntegerMapper;
  }

  @Bean
  public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(
      @Autowired final ObjectMapper objectMapper) {
    final MappingJackson2HttpMessageConverter bean = new MappingJackson2HttpMessageConverter();
    bean.setObjectMapper(objectMapper);

    bean.registerObjectMappersForType(
        UserTaskUpdateRequest.class,
        m -> m.put(MediaType.APPLICATION_JSON, strictIntegerObjectMapper()));
    bean.registerObjectMappersForType(
        UserTaskSearchQueryRequest.class,
        m -> m.put(MediaType.APPLICATION_JSON, strictIntegerObjectMapper()));
    return bean;
  }
}

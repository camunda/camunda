/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class GatewayObjectMapper {

  private static ObjectMapper strictIntegerMapper;

  private ObjectMapper strictIntegerObjectMapper(final ObjectMapper objectMapper) {
    if (strictIntegerMapper == null) {
      strictIntegerMapper = objectMapper.copy().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
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
        m -> m.put(MediaType.APPLICATION_JSON, strictIntegerObjectMapper(objectMapper)));
    bean.registerObjectMappersForType(
        UserTaskSearchQueryRequest.class,
        m -> m.put(MediaType.APPLICATION_JSON, strictIntegerObjectMapper(objectMapper)));
    return bean;
  }
}

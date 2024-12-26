/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonMapperConfiguration {
  private final ObjectMapper objectMapper;

  @Autowired
  public JsonMapperConfiguration(@Autowired(required = false) final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Bean(name = "zeebeJsonMapper")
  @ConditionalOnMissingBean
  public JsonMapper jsonMapper() {
    if (objectMapper == null) {
      return new CamundaObjectMapper();
    }
    return new CamundaObjectMapper(objectMapper);
  }
}

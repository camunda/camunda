/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * This configuration provides different {@link MappingJackson2HttpMessageConverter}s for the
 * different API versions served.
 *
 * <p>This allows us to isolate potential conflicting configurations of the {@link ObjectMapper}s
 * used in the different APIs.
 */
@Configuration
public class MappingJackson2HttpMessageConverterConfiguration {

  @Bean
  @Order(1)
  public MappingJackson2HttpMessageConverter gatewayRestMappingJackson2HttpMessageConverter(
      @Lazy @Qualifier("gatewayRestObjectMapper") final ObjectMapper objectMapper) {
    final PackageSpecificJackson2HttpMessageConverter messageConverter =
        new PackageSpecificJackson2HttpMessageConverter("io.camunda.zeebe");
    messageConverter.setObjectMapper(objectMapper);
    return messageConverter;
  }

  @Bean
  @Order(2)
  public MappingJackson2HttpMessageConverter operateV1MappingJackson2HttpMessageConverter(
      @Lazy @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    final PackageSpecificJackson2HttpMessageConverter messageConverter =
        new PackageSpecificJackson2HttpMessageConverter("io.camunda.operate");
    messageConverter.setObjectMapper(objectMapper);
    return messageConverter;
  }

  @Bean
  @Order(3)
  public MappingJackson2HttpMessageConverter tasklistV1MappingJackson2HttpMessageConverter(
      @Lazy @Qualifier("tasklistObjectMapper") final ObjectMapper objectMapper) {
    final PackageSpecificJackson2HttpMessageConverter messageConverter =
        new PackageSpecificJackson2HttpMessageConverter("io.camunda.tasklist");
    messageConverter.setObjectMapper(objectMapper);
    return messageConverter;
  }

  @Bean
  @Order(4)
  public MappingJackson2HttpMessageConverter defaultRestMappingJackson2HttpMessageConverter(
      final ObjectMapper objectMapper) {
    return new MappingJackson2HttpMessageConverter(objectMapper);
  }
}

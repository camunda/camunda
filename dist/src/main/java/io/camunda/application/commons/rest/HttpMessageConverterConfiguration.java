/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * This configuration provides different {@link
 * org.springframework.http.converter.HttpMessageConverter}s for the different API versions served.
 *
 * <p>This allows us to isolate potential conflicting configurations of the {@link ObjectMapper}s
 * used in the different APIs.
 */
@Configuration
public class HttpMessageConverterConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public StringHttpMessageConverter stringHttpMessageConverter() {
    // We need to register this converter with highest precedence, as otherwise custom
    // Jackson converters are called in cases where we want to parse structured responses to
    // a plain String. This will cause failure as Jackson does not allow to deserialize
    // objects to plain Strings.
    // This behavior (custom converters taking precedence) changed with Spring Boot 4.0
    return new StringHttpMessageConverter(StandardCharsets.UTF_8);
  }

  @Bean
  @Order(1)
  @ConditionalOnRestGatewayEnabled
  public MappingJackson2HttpMessageConverter gatewayRestMappingJackson2HttpMessageConverter(
      @Qualifier("gatewayRestObjectMapper") final ObjectMapper objectMapper) {
    final PackageSpecificJackson2HttpMessageConverter messageConverter =
        new PackageSpecificJackson2HttpMessageConverter("io.camunda.gateway.protocol.model");
    messageConverter.setObjectMapper(objectMapper);
    return messageConverter;
  }

  @Bean
  @Order(2)
  @Profile("operate")
  public MappingJackson2HttpMessageConverter operateV1MappingJackson2HttpMessageConverter(
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    final PackageSpecificJackson2HttpMessageConverter messageConverter =
        new PackageSpecificJackson2HttpMessageConverter("io.camunda.operate");
    messageConverter.setObjectMapper(objectMapper);
    return messageConverter;
  }

  @Bean
  @Order(3)
  @Profile("tasklist")
  public MappingJackson2HttpMessageConverter tasklistV1MappingJackson2HttpMessageConverter(
      @Qualifier("tasklistObjectMapper") final ObjectMapper objectMapper) {
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

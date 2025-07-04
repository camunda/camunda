/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.gateway.rest.controller.EndpointAccessErrorFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiFiltersConfiguration {

  @ConditionalOnExpression("'${camunda.security.authentication.oidc.groupsClaim:}' != ''")
  @Bean
  public FilterRegistrationBean<EndpointAccessErrorFilter> registerFilter(
      final ObjectMapper objectMapper) {
    final FilterRegistrationBean<EndpointAccessErrorFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(new EndpointAccessErrorFilter(objectMapper));
    registration.addUrlPatterns("/v2/groups/*");
    registration.setOrder(1);
    return registration;
  }

  @ConditionalOnExpression("'${camunda.security.multiTenancy.apiEnabled:}' == 'false'")
  @Bean
  public FilterRegistrationBean<EndpointAccessErrorFilter> disableMultiTenancyApiFilter(
      final ObjectMapper objectMapper) {
    final FilterRegistrationBean<EndpointAccessErrorFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(new EndpointAccessErrorFilter(objectMapper));
    registration.addUrlPatterns("/v2/tenants/*");
    registration.setOrder(1);
    return registration;
  }
}

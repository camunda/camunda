/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import static io.camunda.security.configuration.MultiTenancyConfiguration.API_ENABLED_PROPERTY;
import static io.camunda.security.configuration.OidcAuthenticationConfiguration.GROUPS_CLAIM_PROPERTY;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.gateway.rest.controller.EndpointAccessErrorFilter;
import io.camunda.zeebe.util.VisibleForTesting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiFiltersConfiguration {

  @VisibleForTesting
  public static final String GROUPS_API_DISABLED_ERROR_MESSAGE =
      "Groups API is disabled because the application is configured in group claim mode. To use the Groups API, reconfigure the application to manage group mode removing the '%s' property."
          .formatted(GROUPS_CLAIM_PROPERTY);

  @VisibleForTesting
  public static final String USERS_API_DISABLED_ERROR_MESSAGE =
      "Users API is disabled because the application is configured in OIDC mode.";

  @VisibleForTesting
  public static final String TENANTS_API_DISABLED_ERROR_MESSAGE =
      "Tenants API is disabled. Enable the API by setting '%s' to true."
          .formatted(API_ENABLED_PROPERTY);

  @ConditionalOnExpression("'${camunda.security.authentication.oidc.groupsClaim:}' != ''")
  @Bean
  public FilterRegistrationBean<EndpointAccessErrorFilter> disableGroupApiFilter(
      final ObjectMapper objectMapper) {
    final FilterRegistrationBean<EndpointAccessErrorFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(
        new EndpointAccessErrorFilter(objectMapper, GROUPS_API_DISABLED_ERROR_MESSAGE));
    registration.addUrlPatterns("/v2/groups/*");
    registration.setOrder(1);
    return registration;
  }

  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  @Bean
  public FilterRegistrationBean<EndpointAccessErrorFilter> disableUserApiFilter(
      final ObjectMapper objectMapper) {
    final FilterRegistrationBean<EndpointAccessErrorFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(
        new EndpointAccessErrorFilter(objectMapper, USERS_API_DISABLED_ERROR_MESSAGE));
    registration.addUrlPatterns("/v2/users/*");
    registration.setOrder(1);
    return registration;
  }

  @ConditionalOnExpression("'${camunda.security.multiTenancy.apiEnabled:}' == 'false'")
  @Bean
  public FilterRegistrationBean<EndpointAccessErrorFilter> disableMultiTenancyApiFilter(
      final ObjectMapper objectMapper) {
    final FilterRegistrationBean<EndpointAccessErrorFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(
        new EndpointAccessErrorFilter(objectMapper, TENANTS_API_DISABLED_ERROR_MESSAGE));
    registration.addUrlPatterns("/v2/tenants/*");
    registration.setOrder(1);
    return registration;
  }
}

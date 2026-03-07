/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.domain.model.AuthenticationMethod;
import io.camunda.auth.domain.spi.AdminUserCheckProvider;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.domain.spi.WebComponentAccessProvider;
import io.camunda.auth.spring.SecurityFilterChainCustomizer;
import io.camunda.auth.spring.filter.AdminUserCheckFilter;
import io.camunda.auth.spring.filter.WebComponentAuthorizationCheckFilter;
import io.camunda.auth.starter.condition.ConditionalOnAuthenticationMethod;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Auto-configuration that registers {@link SecurityFilterChainCustomizer} beans to add webapp
 * filters (admin user check, component authorization) to any webapp filter chain (OIDC or basic).
 */
@AutoConfiguration(after = CamundaWebappSecurityAutoConfiguration.class)
@ConditionalOnProperty(name = "camunda.auth.security.webapp-enabled", havingValue = "true")
public class CamundaWebappFiltersAutoConfiguration {

  @Bean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnBean(AdminUserCheckProvider.class)
  public SecurityFilterChainCustomizer adminUserCheckCustomizer(
      final AdminUserCheckProvider adminUserCheckProvider) {
    return http ->
        http.addFilterBefore(
            new AdminUserCheckFilter(adminUserCheckProvider), AuthorizationFilter.class);
  }

  @Bean
  @ConditionalOnBean(WebComponentAccessProvider.class)
  public SecurityFilterChainCustomizer webComponentAccessCustomizer(
      final CamundaAuthenticationProvider authenticationProvider,
      final WebComponentAccessProvider webComponentAccessProvider) {
    return http ->
        http.addFilterAfter(
            new WebComponentAuthorizationCheckFilter(
                authenticationProvider, webComponentAccessProvider),
            AuthorizationFilter.class);
  }
}

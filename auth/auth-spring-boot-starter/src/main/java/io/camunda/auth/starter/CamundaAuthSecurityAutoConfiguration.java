/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.spring.filter.OAuth2RefreshTokenFilter;
import io.camunda.auth.spring.handler.AuthFailureHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Auto-configuration for security filter chain components. Provides common security beans
 * (handlers, filters) that consumers can customize or override.
 *
 * <p>This does NOT create a {@code SecurityFilterChain} bean — consumers are expected to configure
 * their own filter chains using Spring Security's {@code HttpSecurity} DSL. This auto-configuration
 * provides the building blocks (handlers, filters, converters) that can be wired into those filter
 * chains.
 */
@AutoConfiguration(after = CamundaAuthAutoConfiguration.class)
@ConditionalOnProperty(name = "camunda.auth.method", havingValue = "oidc", matchIfMissing = true)
public class CamundaAuthSecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AuthFailureHandler authFailureHandler() {
    return new AuthFailureHandler();
  }

  @Bean
  @ConditionalOnMissingBean
  public OAuth2RefreshTokenFilter oAuth2RefreshTokenFilter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OAuth2AuthorizedClientManager authorizedClientManager) {
    return new OAuth2RefreshTokenFilter(authorizedClientRepository, authorizedClientManager);
  }
}

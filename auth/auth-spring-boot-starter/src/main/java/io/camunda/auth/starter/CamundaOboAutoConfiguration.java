/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.spring.OnBehalfOfTokenRelayFilter;
import io.camunda.auth.starter.config.CamundaAuthProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Auto-configuration for OBO flow, enabled when {@code camunda.auth.obo.enabled=true}.
 *
 * <p>This configuration creates the {@link OnBehalfOfTokenRelayFilter} that leverages Spring
 * Security's {@link OAuth2AuthorizedClientManager} for token exchange, following the standard
 * Spring Security OAuth2 Client pattern.
 */
@AutoConfiguration(after = CamundaAuthorizationGrantAutoConfiguration.class)
@ConditionalOnProperty(name = "camunda.auth.obo.enabled", havingValue = "true")
@ConditionalOnBean(OAuth2AuthorizedClientManager.class)
public class CamundaOboAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OnBehalfOfTokenRelayFilter onBehalfOfTokenRelayFilter(
      final OAuth2AuthorizedClientManager authorizedClientManager,
      final CamundaAuthProperties properties) {
    final String registrationId = properties.getTokenExchange().getClientRegistrationId();
    return new OnBehalfOfTokenRelayFilter(
        authorizedClientManager, registrationId != null ? registrationId : "token-exchange");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import io.camunda.authentication.CamundaJwtAuthenticationConverter;
import io.camunda.authentication.CamundaOAuthPrincipalService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

/** Additional dependency beans for the OIDC setup */
@Configuration
public class WebSecurityOidcTestContext {

  @Bean
  public CamundaJwtAuthenticationConverter createJwtConverter(
      CamundaOAuthPrincipalService camundaOAuthPrincipalService) {
    return new CamundaJwtAuthenticationConverter(camundaOAuthPrincipalService);
  }

  @Bean
  public CamundaOAuthPrincipalService createOAuthPrincipalService() {
    return new TestCamundaOAuthPrincipalService();
  }

  @Bean
  public OAuth2AuthorizedClientService authorizedClientService() {
    return new TestOAuth2AuthorizedClientService();
  }
}

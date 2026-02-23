/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.authentication.service.MembershipService;
import io.camunda.authentication.service.NoDBMembershipService;
import io.camunda.search.clients.auth.DisabledResourceAccessProvider;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.ResourceAccessProvider;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class OidcFlowTestContext {

  @Bean
  public OAuth2AuthorizedClientService authorizedClientService(
      final ClientRegistrationRepository clientRegistrationRepository) {
    return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
  }

  @Bean
  public TestApiController createTestController(
      final CamundaAuthenticationProvider authenticationProvider) {
    return new TestApiController(authenticationProvider);
  }

  @Bean
  public CamundaAuthenticationProvider createCamundaAuthenticationProvider() {
    return () ->
        new CamundaAuthentication(
            "dummyUsername", null, false, List.of(), List.of(), List.of(), List.of(), Map.of());
  }

  @Bean
  public ResourceAccessProvider createResourceAccessProvider() {
    return new DisabledResourceAccessProvider();
  }

  @Bean
  public AuthFailureHandler createFailureHandler() {
    return new AuthFailureHandler(new ObjectMapper());
  }

  /**
   * So that camunda.security properties can be used in tests; must be prefixed with
   * 'camunda.security' because this prefix is hardcoded in AuthenticationProperties.
   */
  @SuppressWarnings("ConfigurationProperties")
  @Bean
  @ConfigurationProperties("camunda.security")
  public SecurityConfiguration createSecurityConfiguration() {
    return new SecurityConfiguration();
  }

  @Bean
  public MembershipService createMembershipService(
      final SecurityConfiguration securityConfiguration) {
    return new NoDBMembershipService(securityConfiguration);
  }
}

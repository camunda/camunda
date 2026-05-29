/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.authentication.service.NoDBMembershipService;
import io.camunda.search.clients.auth.DisabledResourceAccessProvider;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.port.in.ResourcePermissionPort;
import io.camunda.security.core.port.out.AuthorizationRepositoryPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
@EnableConfigurationProperties(CamundaSecurityLibraryProperties.class)
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

  /**
   * Permissive {@link ResourcePermissionPort} so CSL's webapp authorization filter does not deny
   * access. Slice tests don't wire OC's authorization data store; without an override CSL's default
   * {@code ResourcePermissionService} would resolve no permissions for the authenticated test
   * principal and redirect every webapp request to {@code /<webapp>/forbidden}.
   */
  @Bean
  public ResourcePermissionPort resourcePermissionPort() {
    return (authentication, resourceType, resourceId, permissionType) -> true;
  }

  /**
   * Empty {@link AuthorizationRepositoryPort} so the host's {@code AuthorizationRepositoryAdapter}
   * (gated on secondary storage being enabled, which is the slice-test default) backs off via
   * {@code @ConditionalOnMissingBean} and doesn't try to autowire an {@code AuthorizationReader}
   * the slice context doesn't pull in.
   */
  @Bean
  public AuthorizationRepositoryPort authorizationRepositoryPort() {
    return (authentication, resourceType) -> java.util.Set.of();
  }

  // CSL's default JsonProblemDetailAuthFailureHandler requires an ObjectMapper; slice tests don't
  // pull JacksonAutoConfiguration so we provide one explicitly.
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public MembershipPort createMembershipPort(final CamundaSecurityLibraryProperties properties) {
    return new NoDBMembershipService(properties);
  }
}

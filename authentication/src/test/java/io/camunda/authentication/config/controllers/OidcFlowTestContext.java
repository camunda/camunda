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
import io.camunda.authentication.service.PhysicalTenantResourceAccessProvider;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.DisabledResourceAccessProvider;
import io.camunda.security.core.authz.ResourceAccessProvider;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.MembershipPort;
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

  @Bean
  public PhysicalTenantResourceAccessProvider physicalTenantResourceAccessProvider(
      final ResourceAccessProvider resourceAccessProvider) {
    return new PhysicalTenantResourceAccessProvider(
        Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, resourceAccessProvider));
  }

  /**
   * Permissive {@link AuthorizationCheckPort} so CSL's webapp authorization filter does not deny
   * access. Slice tests don't wire OC's authorization data store; without an override the host's
   * production {@link AuthorizationCheckPort} would resolve no permissions for the authenticated
   * test principal and redirect every webapp request to {@code /<webapp>/forbidden}.
   */
  @Bean
  public AuthorizationCheckPort authorizationCheckPort() {
    return new AuthorizationCheckPort() {
      @Override
      public <T> Either<AuthorizationRejection, Void> check(
          final CamundaAuthentication authentication,
          final RequiredAuthorization<T> authorization) {
        return Either.right(null);
      }

      @Override
      public <T> Either<AuthorizationRejection, Void> check(
          final Map<String, Object> claims, final RequiredAuthorization<T> authorization) {
        return Either.right(null);
      }

      @Override
      public <T> Either<AuthorizationRejection, Void> check(
          final CamundaAuthentication authentication,
          final RequiredAuthorization<T> authorization,
          final T resource) {
        return Either.right(null);
      }
    };
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

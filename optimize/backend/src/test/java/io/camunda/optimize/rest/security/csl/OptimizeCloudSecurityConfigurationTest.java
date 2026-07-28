/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class OptimizeCloudSecurityConfigurationTest {

  @Mock private ConfigurationService configurationService;
  @Mock private AuthConfiguration authConfiguration;
  @Mock private CloudAuthConfiguration cloudAuthConfiguration;
  @Mock private OidcProviderConfigurationPort oidcProviderConfigurationPort;

  private final OptimizeCloudSecurityConfiguration config =
      new OptimizeCloudSecurityConfiguration();
  private final CamundaSecurityLibraryProperties cslProperties =
      new CamundaSecurityLibraryProperties();

  private OAuth2TokenValidator<Jwt> sharedValidator() {
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(authConfiguration.getCloudAuthConfiguration()).thenReturn(cloudAuthConfiguration);
    lenient().when(cloudAuthConfiguration.getOrganizationId()).thenReturn("org-1");
    lenient().when(cloudAuthConfiguration.getClusterId()).thenReturn("cluster-1");
    when(oidcProviderConfigurationPort.getOidcAuthenticationConfigurations()).thenReturn(Map.of());

    final TokenValidatorFactory factory =
        config.tokenValidatorFactory(
            oidcProviderConfigurationPort, configurationService, cslProperties);
    return factory.createTokenValidator(clientRegistration());
  }

  @Test
  void shouldAcceptBearerTokenScopedToConfiguredCluster() {
    final OAuth2TokenValidator<Jwt> validator = sharedValidator();

    assertThat(
            validator
                .validate(jwt(Map.of(OptimizeCloudClusterValidator.CLUSTER_CLAIM, "cluster-1")))
                .hasErrors())
        .isFalse();
  }

  @Test
  void shouldRejectBearerTokenScopedToAnotherCluster() {
    final OAuth2TokenValidator<Jwt> validator = sharedValidator();

    assertThat(
            validator
                .validate(jwt(Map.of(OptimizeCloudClusterValidator.CLUSTER_CLAIM, "other")))
                .hasErrors())
        .isTrue();
  }

  @Test
  void shouldAcceptLoginTokenForConfiguredOrgWithoutClusterId() {
    final OAuth2TokenValidator<Jwt> validator = sharedValidator();

    final Jwt idToken =
        jwt(
            Map.of(
                OptimizeCloudOrganizationValidator.ORGANIZATIONS_CLAIM,
                List.of(Map.of("id", "org-1", "roles", List.of("analyst")))));

    assertThat(validator.validate(idToken).hasErrors()).isFalse();
  }

  @Test
  void shouldRejectLoginTokenForAnotherOrg() {
    final OAuth2TokenValidator<Jwt> validator = sharedValidator();

    final Jwt idToken =
        jwt(
            Map.of(
                OptimizeCloudOrganizationValidator.ORGANIZATIONS_CLAIM,
                List.of(Map.of("id", "org-2", "roles", List.of("admin")))));

    assertThat(validator.validate(idToken).hasErrors()).isTrue();
  }

  @Test
  void shouldAcceptTokenCarryingNeitherClaim() {
    // Lenient on absence (OC baseline): the shared chain does not reject a claim-less token.
    final OAuth2TokenValidator<Jwt> validator = sharedValidator();

    assertThat(validator.validate(jwt(Map.of())).hasErrors()).isFalse();
  }

  @Test
  void shouldBuildOidcIdTokenDecoderFactoryForLogin() {
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(authConfiguration.getCloudAuthConfiguration()).thenReturn(cloudAuthConfiguration);
    when(cloudAuthConfiguration.getOrganizationId()).thenReturn("org-1");
    when(cloudAuthConfiguration.getClusterId()).thenReturn("cluster-1");
    when(oidcProviderConfigurationPort.getOidcAuthenticationConfigurations()).thenReturn(Map.of());

    final TokenValidatorFactory factory =
        config.tokenValidatorFactory(
            oidcProviderConfigurationPort, configurationService, cslProperties);

    assertThat(config.idTokenDecoderFactory(factory)).isInstanceOf(OidcIdTokenDecoderFactory.class);
  }

  @Test
  void shouldFailStartupWhenOrganizationIdIsBlank() {
    // Fail closed: a blank org id must not silently drop the CCSaaS org access-control gate.
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(authConfiguration.getCloudAuthConfiguration()).thenReturn(cloudAuthConfiguration);
    when(cloudAuthConfiguration.getOrganizationId()).thenReturn("  ");

    assertThatThrownBy(
            () ->
                config.tokenValidatorFactory(
                    oidcProviderConfigurationPort, configurationService, cslProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("organizationId");
  }

  @Test
  void shouldFailStartupWhenClusterIdIsBlank() {
    // Fail closed: a blank cluster id must not silently drop the CCSaaS cluster access-control
    // gate.
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(authConfiguration.getCloudAuthConfiguration()).thenReturn(cloudAuthConfiguration);
    when(cloudAuthConfiguration.getOrganizationId()).thenReturn("org-1");
    when(cloudAuthConfiguration.getClusterId()).thenReturn("");

    assertThatThrownBy(
            () ->
                config.tokenValidatorFactory(
                    oidcProviderConfigurationPort, configurationService, cslProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("clusterId");
  }

  private static ClientRegistration clientRegistration() {
    return ClientRegistration.withRegistrationId("oidc")
        .clientId("optimize")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/api/authentication/callback")
        .authorizationUri("http://idp/authorize")
        .tokenUri("http://idp/token")
        .build();
  }

  private static Jwt jwt(final Map<String, Object> claims) {
    final Instant now = Instant.now();
    final Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .subject("user");
    claims.forEach(builder::claim);
    return builder.build();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
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

  @Test
  void shouldComposeOrgAndClusterValidatorsIntoTheFactory() {
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(authConfiguration.getCloudAuthConfiguration()).thenReturn(cloudAuthConfiguration);
    when(cloudAuthConfiguration.getOrganizationId()).thenReturn("org-1");
    when(cloudAuthConfiguration.getClusterId()).thenReturn("cluster-1");
    when(oidcProviderConfigurationPort.getOidcAuthenticationConfigurations()).thenReturn(Map.of());

    final TokenValidatorFactory factory =
        config.tokenValidatorFactory(oidcProviderConfigurationPort, configurationService);
    final OAuth2TokenValidator<Jwt> validator = factory.createTokenValidator(clientRegistration());

    assertThat(validator.validate(jwt("org-1", "analyst", "cluster-1")).hasErrors()).isFalse();
    // Wrong cluster is rejected by the cluster validator.
    assertThat(validator.validate(jwt("org-1", "analyst", "other")).hasErrors()).isTrue();
    // Disallowed org role is rejected by the organization validator.
    assertThat(validator.validate(jwt("org-1", "viewer", "cluster-1")).hasErrors()).isTrue();
  }

  @Test
  void shouldProvideIdTokenDecoderFactory() {
    final TokenValidatorFactory factory =
        new TokenValidatorFactory(Map.of(), OidcConfiguration.DEFAULT_CLOCK_SKEW, List.of());

    assertThat(config.idTokenDecoderFactory(factory)).isInstanceOf(OidcIdTokenDecoderFactory.class);
  }

  private static Jwt jwt(final String orgId, final String role, final String clusterId) {
    final Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .subject("user")
        .claim(
            OptimizeCloudOrganizationValidator.ORGANIZATIONS_CLAIM,
            List.of(Map.of("id", orgId, "roles", List.of(role))))
        .claim(OptimizeCloudClusterValidator.CLUSTER_ID_CLAIM, clusterId)
        .build();
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
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.cloud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.security.UserIdMigrationService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class CCSaaSSecurityConfigurerAdapterTest {

  private static final String CLIENT_ID = "optimize-client-id";
  private static final String ISSUER = "https://tenant.auth0.com/";
  private static final String ORGANIZATION_ID = "org-1";

  @Mock private ConfigurationService configurationService;
  @Mock private CustomPreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider;
  @Mock private SessionService sessionService;
  @Mock private AuthCookieService authCookieService;
  @Mock private ClientRegistrationRepository clientRegistrationRepository;
  @Mock private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
  @Mock private UserIdMigrationService userIdMigrationService;
  @Mock private AuthConfiguration authConfiguration;
  @Mock private CloudAuthConfiguration cloudAuthConfiguration;

  private CCSaaSSecurityConfigurerAdapter adapter;

  @BeforeEach
  void setUp() {
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(authConfiguration.getCloudAuthConfiguration()).thenReturn(cloudAuthConfiguration);
    when(cloudAuthConfiguration.getOrganizationId()).thenReturn(ORGANIZATION_ID);

    adapter =
        new CCSaaSSecurityConfigurerAdapter(
            configurationService,
            preAuthenticatedAuthenticationProvider,
            sessionService,
            authCookieService,
            clientRegistrationRepository,
            oAuth2AuthorizedClientService,
            userIdMigrationService);
  }

  private OAuth2TokenValidator<Jwt> validator() {
    return adapter.createIdTokenValidators(clientRegistration());
  }

  @Test
  void shouldAcceptIdTokenWithValidIssuerAudienceAndRole() {
    final OAuth2TokenValidator<Jwt> validator = validator();

    assertThat(validator.validate(idToken(ISSUER, CLIENT_ID, ORGANIZATION_ID)).hasErrors())
        .isFalse();
  }

  @Test
  void shouldRejectIdTokenWithMismatchedIssuer() {
    final OAuth2TokenValidator<Jwt> validator = validator();

    assertThat(
            validator
                .validate(
                    idToken("https://a-different-tenant.auth0.com/", CLIENT_ID, ORGANIZATION_ID))
                .hasErrors())
        .isTrue();
  }

  @Test
  void shouldRejectIdTokenWithMismatchedAudience() {
    final OAuth2TokenValidator<Jwt> validator = validator();

    assertThat(
            validator
                .validate(idToken(ISSUER, "some-other-registered-app", ORGANIZATION_ID))
                .hasErrors())
        .isTrue();
  }

  @Test
  void shouldRejectIdTokenWithDisallowedOrganization() {
    final OAuth2TokenValidator<Jwt> validator = validator();

    assertThat(validator.validate(idToken(ISSUER, CLIENT_ID, "some-other-org")).hasErrors())
        .isTrue();
  }

  private static ClientRegistration clientRegistration() {
    return ClientRegistration.withRegistrationId("auth0")
        .clientId(CLIENT_ID)
        .clientSecret("secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/sso-callback")
        .authorizationUri("https://custom.auth0.com/authorize")
        .tokenUri("https://tenant.auth0.com/oauth/token")
        .issuerUri(ISSUER)
        .build();
  }

  private static Jwt idToken(
      final String issuer, final String audience, final String organizationId) {
    final Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuer(issuer)
        .audience(List.of(audience))
        .subject("user-1")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .claim(
            "https://camunda.com/orgs",
            List.of(Map.of("id", organizationId, "roles", List.of("admin"))))
        .build();
  }
}

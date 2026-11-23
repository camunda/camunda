/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.zeebe.auth.Authorization.IS_CAMUNDA_GROUPS_ENABLED;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static java.net.http.HttpClient.newHttpClient;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
import io.camunda.authentication.converter.TokenClaimsConverter;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Two OIDC providers, of which only {@code foo} reads the groups from a claim. The claim is {@code
 * azp}, which every Keycloak access token has, so no realm needs a groups mapper.
 */
@SuppressWarnings("SpringBootApplicationProperties")
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(
    classes = {
      OidcFlowTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.providers.oidc.foo.client-id="
          + MultipleOidcProviderGroupsIT.REALM_FOO_CLIENT_ID,
      "camunda.security.authentication.providers.oidc.foo.client-secret="
          + MultipleOidcProviderGroupsIT.REALM_FOO_CLIENT_SECRET,
      "camunda.security.authentication.providers.oidc.foo.redirect-uri=http://localhost/sso-callback",
      "camunda.security.authentication.providers.oidc.foo.audiences=camunda-foo",
      "camunda.security.authentication.providers.oidc.foo.groups-claim=azp",
      "camunda.security.authentication.providers.oidc.bar.client-id="
          + MultipleOidcProviderGroupsIT.REALM_BAR_CLIENT_ID,
      "camunda.security.authentication.providers.oidc.bar.client-secret="
          + MultipleOidcProviderGroupsIT.REALM_BAR_CLIENT_SECRET,
      "camunda.security.authentication.providers.oidc.bar.redirect-uri=http://localhost/sso-callback"
    })
@ActiveProfiles("consolidated-auth")
@Testcontainers
class MultipleOidcProviderGroupsIT {

  static final String REALM_FOO = "camunda-foo";
  static final String REALM_FOO_CLIENT_ID = "camunda-foo";
  static final String REALM_FOO_CLIENT_SECRET = "pW7IzLnbYMpPk785irfwoQjBQ3VSQnT3";

  static final String REALM_BAR = "camunda-bar";
  static final String REALM_BAR_CLIENT_ID = "camunda-bar";
  static final String REALM_BAR_CLIENT_SECRET = "kCgndC3n3apTVJ8j76X3Y6hpqbxR7Kvf";

  @Container
  static KeycloakContainer keycloak =
      DefaultTestContainers.createDefaultKeycloak()
          .withRealmImportFiles("/camunda-foo-realm.json", "/camunda-bar-realm.json");

  @Autowired JwtDecoder jwtDecoder;
  @Autowired TokenClaimsConverter tokenClaimsConverter;
  @Autowired SecurityConfiguration securityConfiguration;

  @DynamicPropertySource
  static void properties(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.providers.oidc.foo.issuer-uri",
        () -> keycloak.getAuthServerUrl() + "/realms/" + REALM_FOO);
    registry.add(
        "camunda.security.authentication.providers.oidc.bar.issuer-uri",
        () -> keycloak.getAuthServerUrl() + "/realms/" + REALM_BAR);
  }

  @Test
  void shouldReadGroupsFromTheClaimOfTheProviderThatIssuedTheToken() throws Exception {
    // given
    final var token = accessToken(REALM_FOO, REALM_FOO_CLIENT_ID, REALM_FOO_CLIENT_SECRET);

    // when
    final var authentication = tokenClaimsConverter.convert(jwtDecoder.decode(token).getClaims());

    // then
    assertThat(authentication.authenticatedGroupIds()).containsExactly(REALM_FOO_CLIENT_ID);
  }

  @Test
  void shouldNotReadGroupsFromAProviderWithoutAGroupsClaim() throws Exception {
    // given
    final var token = accessToken(REALM_BAR, REALM_BAR_CLIENT_ID, REALM_BAR_CLIENT_SECRET);

    // when
    final var authentication = tokenClaimsConverter.convert(jwtDecoder.decode(token).getClaims());

    // then
    assertThat(authentication.authenticatedGroupIds()).isEmpty();
  }

  @Test
  void shouldGiveTheBrokerTheGroupsOfTheProviderThatIssuedTheToken() throws Exception {
    // given
    final var brokerConverter = new BrokerRequestAuthorizationConverter(securityConfiguration);
    final var fooToken = accessToken(REALM_FOO, REALM_FOO_CLIENT_ID, REALM_FOO_CLIENT_SECRET);
    final var barToken = accessToken(REALM_BAR, REALM_BAR_CLIENT_ID, REALM_BAR_CLIENT_SECRET);

    // when
    final var fooClaims =
        brokerConverter.convert(
            tokenClaimsConverter.convert(jwtDecoder.decode(fooToken).getClaims()));
    final var barClaims =
        brokerConverter.convert(
            tokenClaimsConverter.convert(jwtDecoder.decode(barToken).getClaims()));

    // then
    assertThat(fooClaims)
        .containsEntry(IS_CAMUNDA_GROUPS_ENABLED, false)
        .containsEntry(USER_GROUPS_CLAIMS, java.util.List.of(REALM_FOO_CLIENT_ID));
    assertThat(barClaims)
        .containsEntry(IS_CAMUNDA_GROUPS_ENABLED, true)
        .doesNotContainKey(USER_GROUPS_CLAIMS);
  }

  private static String accessToken(
      final String realm, final String clientId, final String clientSecret) throws Exception {
    final var tokenEndpoint =
        keycloak.getAuthServerUrl() + "/realms/" + realm + "/protocol/openid-connect/token";
    final var body =
        "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
    try (final HttpClient httpClient = newHttpClient()) {
      final var request =
          HttpRequest.newBuilder()
              .uri(URI.create(tokenEndpoint))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      return new ObjectMapper().readTree(response.body()).get("access_token").asText();
    }
  }
}

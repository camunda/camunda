/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.authentication.config.controllers.TestApiController.*;
import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_UNPROTECTED_ENDPOINT;
import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_V2_API_ENDPOINT;
import static java.net.http.HttpClient.newHttpClient;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings({"SpringBootApplicationProperties", "WrongPropertyKeyValueDelimiter"})
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

      // OIDC provider/realm: camunda-foo
      "camunda.security.authentication.providers.oidc.foo.client-id="
          + MultipleOidcProviderFlowTest.REALM_FOO_CLIENT_ID,
      "camunda.security.authentication.providers.oidc.foo.client-secret="
          + MultipleOidcProviderFlowTest.REALM_FOO_CLIENT_SECRET,
      "camunda.security.authentication.providers.oidc.foo.redirect-uri=http://localhost/sso-callback",
      "camunda.security.authentication.providers.oidc.foo.audiences=camunda-foo",

      // OIDC provider/realm: camunda-bar
      "camunda.security.authentication.providers.oidc.bar.client-id="
          + MultipleOidcProviderFlowTest.REALM_BAR_CLIENT_ID,
      "camunda.security.authentication.providers.oidc.bar.client-secret="
          + MultipleOidcProviderFlowTest.REALM_BAR_CLIENT_SECRET,
      "camunda.security.authentication.providers.oidc.bar.redirect-uri=http://localhost/sso-callback"
    })
@ActiveProfiles("consolidated-auth")
@Testcontainers
class MultipleOidcProviderFlowTest {

  static final String REALM_FOO_CLIENT_ID = "camunda-foo";
  static final String REALM_FOO_CLIENT_SECRET = "pW7IzLnbYMpPk785irfwoQjBQ3VSQnT3";
  static final String REALM_FOO = "camunda-foo";

  static final String REALM_BAR_CLIENT_ID = "camunda-bar";
  static final String REALM_BAR_CLIENT_SECRET = "kCgndC3n3apTVJ8j76X3Y6hpqbxR7Kvf";
  static final String REALM_BAR = "camunda-bar";

  // used to test that access to not configured providers is denied
  static final String REALM_IDENTITY_TEST_CLIENT_ID = "camunda-test";
  static final String REALM_IDENTITY_TEST_CLIENT_SECRET = "yI2oAlOzx2A9AXmiUO0fqT4qNb8l3HBP";
  static final String REALM_IDENTITY_TEST = "camunda-identity-test";

  @Container
  static KeycloakContainer keycloak =
      new KeycloakContainer()
          .withRealmImportFiles(
              "/camunda-foo-realm.json",
              "/camunda-bar-realm.json",
              "/camunda-identity-test-realm.json");

  @Autowired MockMvcTester mockMvcTester;

  @DynamicPropertySource
  static void properties(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.providers.oidc.foo.issuer-uri",
        () -> keycloak.getAuthServerUrl() + "/realms/" + REALM_FOO);
    registry.add(
        "camunda.security.authentication.providers.oidc.bar.issuer-uri",
        () -> keycloak.getAuthServerUrl() + "/realms/" + REALM_BAR);
  }

  @Nested
  class ClientFlow {
    @Test
    public void shouldReturnUnauthorizedWhenClientUnauthenticated() {
      // Given an unauthenticated client
      // When the client accesses a protected API endpoint
      final MvcTestResult result =
          mockMvcTester.get().uri("/api/dummy").accept(MediaType.APPLICATION_JSON).exchange();

      // Then the client receives the http response code 401
      assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldDenyAccessWithInvalidClientCredentialsToken() {
      // Given an invalid token
      final String invalidToken = "invalid-token";
      final MvcTestResult result =
          mockMvcTester
              .get()
              .uri(DUMMY_V2_API_ENDPOINT)
              .accept(MediaType.APPLICATION_JSON)
              .header("Authorization", "Bearer " + invalidToken)
              .exchange();
      assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldObtainTokenUsingRealmFoo() throws Exception {
      // Given valid client credentials
      final String accessToken =
          getClientAccessToken(REALM_FOO, REALM_FOO_CLIENT_ID, REALM_FOO_CLIENT_SECRET);

      // When using the token to access a protected endpoint
      final MvcTestResult apiResult =
          mockMvcTester
              .get()
              .uri(DUMMY_V2_API_ENDPOINT)
              .accept(MediaType.APPLICATION_JSON)
              .header("Authorization", "Bearer " + accessToken)
              .exchange();
      // Then access is granted
      assertThat(apiResult).hasStatus(HttpStatus.OK).hasBodyTextEqualTo(DEFAULT_RESPONSE);
    }

    @Test
    public void shouldObtainTokenUsingRealmBar() throws Exception {
      // Given valid client credentials
      final String accessToken =
          getClientAccessToken(REALM_BAR, REALM_BAR_CLIENT_ID, REALM_BAR_CLIENT_SECRET);

      // When using the token to access a protected endpoint
      final MvcTestResult apiResult =
          mockMvcTester
              .get()
              .uri(DUMMY_V2_API_ENDPOINT)
              .accept(MediaType.APPLICATION_JSON)
              .header("Authorization", "Bearer " + accessToken)
              .exchange();
      // Then access is granted
      assertThat(apiResult).hasStatus(HttpStatus.OK).hasBodyTextEqualTo(DEFAULT_RESPONSE);
    }

    @Test
    public void shouldDenyAccessToUnknownIssuer() throws Exception {
      // Given valid client credentials
      final String accessToken =
          getClientAccessToken(
              REALM_IDENTITY_TEST,
              REALM_IDENTITY_TEST_CLIENT_ID,
              REALM_IDENTITY_TEST_CLIENT_SECRET);

      // When using the token to access a protected endpoint
      final MvcTestResult apiResult =
          mockMvcTester
              .get()
              .uri(DUMMY_V2_API_ENDPOINT)
              .accept(MediaType.APPLICATION_JSON)
              .header("Authorization", "Bearer " + accessToken)
              .exchange();
      // Then access is granted
      assertThat(apiResult).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private static String getClientAccessToken(
        final String realm, final String clientId, final String clientSecret)
        throws IOException, InterruptedException {
      final String tokenEndpoint =
          keycloak.getAuthServerUrl() + "/realms/" + realm + "/protocol/openid-connect/token";
      final String requestBody =
          "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
      final HttpResponse<String> response;
      try (final HttpClient httpClient = newHttpClient()) {
        final HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
      }
      return new ObjectMapper().readTree(response.body()).get("access_token").asText();
    }
  }

  @Nested
  class UserFlow {
    @Test
    public void shouldAllowUnauthenticatedRequestsToUnprotectedApi() {
      final MvcTestResult result =
          mockMvcTester
              .get()
              .uri(DUMMY_UNPROTECTED_ENDPOINT)
              .accept(MediaType.TEXT_HTML)
              .exchange();
      assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    public void shouldRedirectToLoginPageWhenUserUnauthenticated() {
      // Given an unauthenticated user
      // When the user accesses a protected webapp endpoint...
      // (The Accept header is essential here - Spring filters use text/html to match the request to
      // a redirection flow, rather than just returning 401)
      final MvcTestResult result =
          mockMvcTester.get().uri("/").accept(MediaType.TEXT_HTML).exchange();

      // Then the user is redirected to the Login Page listing the OIDC providers
      assertThat(result)
          .hasStatus(HttpStatus.FOUND)
          .hasHeader("Location", "http://localhost/login");
    }

    @Test
    public void shouldRedirectToRealmFoo() {
      // When a user requests Spring's local authorization endpoint
      final MvcTestResult result =
          mockMvcTester
              .get()
              .uri("/oauth2/authorization/foo")
              .accept(MediaType.TEXT_HTML)
              .exchange();

      // Then the response should be a redirect to the OIDC provider's authentication endpoint
      assertThat(result).hasStatus(HttpStatus.FOUND).containsHeader("Location");
      final var locationHeader = result.getResponse().getHeader("Location");
      assertThat(locationHeader)
          .startsWith(
              keycloak.getAuthServerUrl()
                  + "/realms/"
                  + REALM_FOO
                  + "/protocol/openid-connect/auth")
          .contains("client_id=" + REALM_FOO_CLIENT_ID)
          .contains("response_type=code")
          .contains("scope=openid%20profile");
    }

    @Test
    public void shouldRedirectToRealmBar() {
      // When a user requests Spring's local authorization endpoint
      final MvcTestResult result =
          mockMvcTester
              .get()
              .uri("/oauth2/authorization/bar")
              .accept(MediaType.TEXT_HTML)
              .exchange();

      // Then the response should be a redirect to the OIDC provider's authentication endpoint
      assertThat(result).hasStatus(HttpStatus.FOUND).containsHeader("Location");
      final var locationHeader = result.getResponse().getHeader("Location");
      assertThat(locationHeader)
          .startsWith(
              keycloak.getAuthServerUrl()
                  + "/realms/"
                  + REALM_BAR
                  + "/protocol/openid-connect/auth")
          .contains("client_id=" + REALM_BAR_CLIENT_ID)
          .contains("response_type=code")
          .contains("scope=openid%20profile");
    }

    @Test
    public void shouldDenyWithUnknownRealm() {
      // When a user requests Spring's local authorization endpoint
      final MvcTestResult result =
          mockMvcTester
              .get()
              .uri("/oauth2/authorization/unknown")
              .accept(MediaType.TEXT_HTML)
              .exchange();

      // Spring returns 500 in such cases
      assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}

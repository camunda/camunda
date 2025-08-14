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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
      "camunda.security.authentication.oidc.client-id=" + OidcFlowTest.CLIENT_ID,
      "camunda.security.authentication.oidc.client-secret=" + OidcFlowTest.CLIENT_SECRET,
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      // uncomment to debug the filter chain
      //      "logging.level.org.springframework.security=TRACE",
    })
@ActiveProfiles("consolidated-auth")
@Testcontainers
class OidcFlowTest {

  // client and user defined in camunda-identity-test-realm.json
  static final String CLIENT_ID = "camunda-test";
  static final String CLIENT_SECRET = "yI2oAlOzx2A9AXmiUO0fqT4qNb8l3HBP";
  static final String REALM = "camunda-identity-test";
  static final String TEST_USERNAME = "ccamundovski";
  static final String TEST_PASSWORD = "apassword";

  @Container
  static KeycloakContainer keycloak =
      new KeycloakContainer().withRealmImportFile("/camunda-identity-test-realm.json");

  @Autowired MockMvcTester mockMvcTester;

  @DynamicPropertySource
  static void properties(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.oidc.issuer-uri",
        () -> keycloak.getAuthServerUrl() + "/realms/" + REALM);
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
    public void shouldObtainTokenWithClientCredentialsAndAccessProtectedApi() throws Exception {
      // Given valid client credentials
      final String accessToken = getClientAccessToken();

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

    private static String getClientAccessToken() throws IOException, InterruptedException {
      final String tokenEndpoint =
          keycloak.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token";
      final String requestBody =
          "grant_type=client_credentials&client_id="
              + CLIENT_ID
              + "&client_secret="
              + CLIENT_SECRET;
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
    public void shouldRedirectWhenUserUnauthenticated() {
      // Given an unauthenticated user
      // When the user accesses a protected webapp endpoint...
      // (The Accept header is essential here - Spring filters use text/html to match the request to
      // a redirection flow, rather than just returning 401)
      final MvcTestResult result =
          mockMvcTester.get().uri("/").accept(MediaType.TEXT_HTML).exchange();

      // Then the user is redirected to the OIDC authorization endpoint
      assertThat(result)
          .hasStatus(HttpStatus.FOUND)
          .hasHeader("Location", "http://localhost/oauth2/authorization/oidc");
    }

    @Test
    public void shouldRedirectToOidcProviderWhenLocalAuthorizationEndpointIsRequested() {
      // When a user requests Spring's local authorization endpoint
      final MvcTestResult result =
          mockMvcTester
              .get()
              .uri("/oauth2/authorization/oidc")
              .accept(MediaType.TEXT_HTML)
              .exchange();

      // Then the response should be a redirect to the OIDC provider's authentication endpoint
      assertThat(result).hasStatus(HttpStatus.FOUND).containsHeader("Location");
      final var locationHeader = result.getResponse().getHeader("Location");
      assertThat(locationHeader)
          .startsWith(
              keycloak.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/auth")
          .contains("client_id=" + CLIENT_ID)
          .contains("response_type=code")
          .contains("scope=openid%20profile");
    }

    @Test
    public void shouldAllowAccessToProtectedWebEndpointWithValidUserToken() throws Exception {
      // Given valid user credentials
      final String accessToken = getUserAccessToken();

      // When using the token to access a protected web endpoint
      final MvcTestResult result =
          mockMvcTester
              .get()
              .uri(DUMMY_WEBAPP_ENDPOINT)
              .accept(MediaType.TEXT_HTML)
              .header("Authorization", "Bearer " + accessToken)
              .exchange();

      // Then access is granted
      assertThat(result).hasStatus(HttpStatus.OK).hasBodyTextEqualTo(DEFAULT_RESPONSE);
    }

    private String getUserAccessToken() throws IOException, InterruptedException {
      final String tokenEndpoint =
          keycloak.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token";
      final String requestBody =
          "grant_type=password&client_id="
              + CLIENT_ID
              + "&client_secret="
              + CLIENT_SECRET
              + "&username="
              + TEST_USERNAME
              + "&password="
              + TEST_PASSWORD;
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
}

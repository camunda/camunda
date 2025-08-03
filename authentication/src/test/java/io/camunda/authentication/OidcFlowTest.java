/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_UNPROTECTED_ENDPOINT;
import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
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
      "camunda.security.authentication.oidc.client-id=camunda-test",
      "camunda.security.authentication.oidc.client-secret=yI2oAlOzx2A9AXmiUO0fqT4qNb8l3HBP",
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      // uncomment to debug the filter chain
      //      "logging.level.org.springframework.security=TRACE",
    })
@ActiveProfiles("consolidated-auth")
@Testcontainers
class OidcFlowTest {

  @Container
  static KeycloakContainer keycloak =
      new KeycloakContainer().withRealmImportFile("/camunda-identity-test-realm.json");

  @Autowired MockMvcTester mockMvcTester;

  @DynamicPropertySource
  static void properties(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.oidc.issuer-uri",
        () -> keycloak.getAuthServerUrl() + "/realms/camunda-identity-test");
  }

  @Test
  public void shouldAllowUnauthenticatedRequestsToUnprotectedApi() {
    final MvcTestResult result =
        mockMvcTester.get().uri(DUMMY_UNPROTECTED_ENDPOINT).accept(MediaType.TEXT_HTML).exchange();
    assertThat(result).hasStatus(HttpStatus.OK);
  }

  @Test
  public void shouldRedirectWhenUserUnauthenticated() {
    // Given an unauthenticated user
    // When the user accesses a protected webapp endpoint
    // The Accept header is essential here - Spring filters use text/html to match the request to a
    // redirection flow, rather than just returning 401
    final MvcTestResult result =
        mockMvcTester.get().uri("/").accept(MediaType.TEXT_HTML).exchange();

    // Then the user is redirected to the OIDC authorization endpoint
    assertThat(result)
        .hasStatus(HttpStatus.FOUND)
        .hasHeader("Location", "http://localhost/oauth2/authorization/oidc");
  }

  @Test
  public void shouldReturnUnauthorizedWhenClientUnauthenticated() {
    // Given an unauthenticated client
    // When the client accesses a protected API endpoint
    final MvcTestResult result =
        mockMvcTester.get().uri("/api/dummy").accept(MediaType.APPLICATION_JSON).exchange();

    // Then the client receives the http response code 401
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }
}

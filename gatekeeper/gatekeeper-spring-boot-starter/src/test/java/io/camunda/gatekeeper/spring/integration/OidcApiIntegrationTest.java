/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.gatekeeper.spring.integration.app.TestComponentApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OIDC API integration test that boots a Spring Boot application with gatekeeper configured for
 * OIDC authentication. Uses a Keycloak Testcontainer as the identity provider. Tests verify the
 * Bearer JWT authentication pipeline for API endpoints.
 *
 * <p>This test mirrors {@link BasicAuthIntegrationTest} but uses OIDC/JWT instead of HTTP Basic.
 */
@SpringBootTest(classes = TestComponentApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("oidc")
final class OidcApiIntegrationTest {

  @Autowired private MockMvc mockMvc;

  private static String demoToken;
  private static String operatorToken;

  @DynamicPropertySource
  static void configureOidc(final DynamicPropertyRegistry registry) {
    KeycloakTestSupport.configureOidc(registry);
  }

  @BeforeAll
  static void obtainTokens() throws Exception {
    demoToken = KeycloakTestSupport.obtainAccessToken("demo", "demo");
    operatorToken = KeycloakTestSupport.obtainAccessToken("operator", "operator");
  }

  @Nested
  @DisplayName("Protected API endpoints")
  class ProtectedApiTests {

    @Test
    @DisplayName("unauthenticated request returns 401")
    void unauthenticatedRequestShouldReturn401() throws Exception {
      mockMvc.perform(get("/v2/test/identity")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("invalid token returns 401")
    void invalidTokenShouldReturn401() throws Exception {
      mockMvc
          .perform(
              get("/v2/test/identity").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("valid token returns 200 with resolved identity")
    void validTokenShouldReturnResolvedIdentity() throws Exception {
      mockMvc
          .perform(
              get("/v2/test/identity").header(HttpHeaders.AUTHORIZATION, "Bearer " + demoToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value("demo"))
          .andExpect(jsonPath("$.anonymous").value(false));
    }
  }

  @Nested
  @DisplayName("Membership resolution")
  class MembershipResolutionTests {

    @Test
    @DisplayName("demo user gets correct groups, roles, and tenants from MembershipResolver")
    void demoUserShouldHaveExpectedMemberships() throws Exception {
      mockMvc
          .perform(
              get("/v2/test/identity").header(HttpHeaders.AUTHORIZATION, "Bearer " + demoToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.groups", contains("engineering", "admins")))
          .andExpect(jsonPath("$.roles", contains("operator")))
          .andExpect(jsonPath("$.tenants", contains("tenant-alpha", "tenant-beta")));
    }

    @Test
    @DisplayName("different user gets different memberships")
    void operatorUserShouldHaveDifferentMemberships() throws Exception {
      mockMvc
          .perform(
              get("/v2/test/identity").header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value("operator"))
          .andExpect(jsonPath("$.groups", contains("ops")))
          .andExpect(jsonPath("$.roles", contains("viewer")))
          .andExpect(jsonPath("$.tenants", contains("tenant-alpha")));
    }
  }

  @Nested
  @DisplayName("User info endpoint (CamundaUserProvider pipeline)")
  class UserInfoTests {

    @Test
    @DisplayName("returns assembled user info with profile, components, and memberships")
    void shouldReturnFullUserInfo() throws Exception {
      mockMvc
          .perform(get("/v2/test/user").header(HttpHeaders.AUTHORIZATION, "Bearer " + demoToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.displayName").value("Demo User"))
          .andExpect(jsonPath("$.username").value("demo"))
          .andExpect(jsonPath("$.email").value("demo@example.com"))
          .andExpect(jsonPath("$.canLogout").value(true))
          .andExpect(jsonPath("$.authorizedComponents").isArray())
          .andExpect(jsonPath("$.tenants").isArray())
          .andExpect(jsonPath("$.groups").isArray())
          .andExpect(jsonPath("$.roles").isArray());
    }
  }

  @Nested
  @DisplayName("Unprotected paths")
  class UnprotectedPathTests {

    @Test
    @DisplayName("actuator health is accessible without authentication")
    void healthShouldBeAccessibleWithoutAuth() throws Exception {
      mockMvc
          .perform(get("/actuator/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("unprotected API paths are accessible without authentication")
    void licenseShouldBeAccessibleWithoutAuth() throws Exception {
      mockMvc
          .perform(get("/v2/license"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.type").value("test-license"));
    }
  }
}

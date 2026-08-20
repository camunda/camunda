/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
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

/**
 * End-to-end regression for CSL GH-569: the assembled Orchestration webapp must recognise its own
 * OIDC authorization-code callback when served under a servlet context-path.
 *
 * <p>The Camunda 8.10 chart renders {@code camunda.security.authentication.oidc.redirect-uri} as an
 * absolute URL embedding the context-path (e.g. {@code https://host/orchestration/sso-callback}
 * under {@code server.servlet.context-path=/orchestration}). Spring's redirection-endpoint matcher
 * matches the context-path-relative request path, so if the matcher were left context-prefixed the
 * callback filter would never fire and login would loop indefinitely.
 *
 * <p>This test mirrors {@link OidcFlowTest} but adds a context-path and a context-embedded
 * redirect-uri, then drives the context-relative callback and asserts it is <em>not</em> redirected
 * back into the OIDC flow — the loop signature.
 *
 * <p>NOTE: depends on the CSL context-path fix shipped in alpha58; against alpha57 the callback is
 * instead answered with a 302 to {@code /orchestration/oauth2/authorization/oidc} — the login loop.
 */
@SuppressWarnings({"SpringBootApplicationProperties", "WrongPropertyKeyValueDelimiter"})
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(
    classes = {
      OidcFlowTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "server.servlet.context-path=" + OidcFlowContextPathTest.CONTEXT_PATH,
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=" + OidcFlowContextPathTest.CLIENT_ID,
      "camunda.security.authentication.oidc.client-secret=" + OidcFlowContextPathTest.CLIENT_SECRET,
      "camunda.security.authentication.oidc.redirect-uri=http://localhost"
          + OidcFlowContextPathTest.CONTEXT_PATH
          + OidcFlowContextPathTest.CALLBACK_PATH,
    })
@ActiveProfiles("consolidated-auth")
@Testcontainers
class OidcFlowContextPathTest {

  static final String CLIENT_ID = "camunda-test";
  static final String CLIENT_SECRET = "yI2oAlOzx2A9AXmiUO0fqT4qNb8l3HBP";
  static final String REALM = "camunda-identity-test";
  static final String CONTEXT_PATH = "/orchestration";
  static final String CALLBACK_PATH = "/sso-callback";

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

  @Test
  public void shouldRecognizeCallbackUnderContextPathAndNotLoop() {
    // given the app runs under /orchestration with a context-embedded redirect-uri
    // when the OIDC authorization-code callback arrives at the context-relative /sso-callback
    // (the servlet container reports the context-path separately, so Spring's redirection-endpoint
    // matcher sees the context-relative path)
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri(CONTEXT_PATH + CALLBACK_PATH)
            .contextPath(CONTEXT_PATH)
            .servletPath(CALLBACK_PATH)
            .param("code", "test-code")
            .param("state", "test-state")
            .accept(MediaType.TEXT_HTML)
            .exchange();

    // then the OAuth2 login filter claims the request (no saved authorization request -> redirect
    // to the context root) rather than falling through to a 302 back into the OIDC flow (the loop)
    assertThat(result).hasStatus(HttpStatus.FOUND);
    final var location = result.getResponse().getHeader("Location");
    assertThat(location)
        .as("callback under a context-path must not be redirected back into the OIDC flow")
        .doesNotContain("/oauth2/authorization");
  }
}

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
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
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
 * Regression test for issue #64495: on a cluster served under a servlet context path (every SaaS
 * Orchestration Cluster instance) with two or more OIDC providers configured, the rendered
 * identity-provider picker page must link to context-path-prefixed authorization URLs. Before the
 * fix (camunda-security-library#697, shipped in 1.1.0), the picker rendered root-relative hrefs
 * that 404'd under any context path.
 *
 * <p>Combines {@link OidcFlowContextPathIT}'s context-path setup with {@link
 * MultipleOidcProviderFlowIT}'s two-provider setup, since the picker only renders (instead of
 * auto-redirecting) when there is an actual choice between two or more providers.
 *
 * <p>Also covers the physical-tenant {@code default} scoped chain, which {@link WebSecurityConfig}
 * wires even with zero {@code camunda.physical-tenants.*} configured: its picker must compose the
 * context path with the chain's own {@code /physical-tenants/default} base path.
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
      "server.servlet.context-path=" + MultipleOidcProviderContextPathFlowIT.CONTEXT_PATH,
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",

      // OIDC provider/realm: camunda-foo
      "camunda.security.authentication.providers.oidc.foo.client-id="
          + MultipleOidcProviderContextPathFlowIT.REALM_FOO_CLIENT_ID,
      "camunda.security.authentication.providers.oidc.foo.client-secret="
          + MultipleOidcProviderContextPathFlowIT.REALM_FOO_CLIENT_SECRET,
      "camunda.security.authentication.providers.oidc.foo.redirect-uri=http://localhost/sso-callback",

      // OIDC provider/realm: camunda-bar
      "camunda.security.authentication.providers.oidc.bar.client-id="
          + MultipleOidcProviderContextPathFlowIT.REALM_BAR_CLIENT_ID,
      "camunda.security.authentication.providers.oidc.bar.client-secret="
          + MultipleOidcProviderContextPathFlowIT.REALM_BAR_CLIENT_SECRET,
      "camunda.security.authentication.providers.oidc.bar.redirect-uri=http://localhost/sso-callback"
    })
@ActiveProfiles("consolidated-auth")
@Testcontainers
class MultipleOidcProviderContextPathFlowIT {

  static final String CONTEXT_PATH = "/orchestration";

  static final String REALM_FOO_CLIENT_ID = "camunda-foo";
  static final String REALM_FOO_CLIENT_SECRET = "pW7IzLnbYMpPk785irfwoQjBQ3VSQnT3";
  static final String REALM_FOO = "camunda-foo";

  static final String REALM_BAR_CLIENT_ID = "camunda-bar";
  static final String REALM_BAR_CLIENT_SECRET = "kCgndC3n3apTVJ8j76X3Y6hpqbxR7Kvf";
  static final String REALM_BAR = "camunda-bar";

  @Container
  static KeycloakContainer keycloak =
      DefaultTestContainers.createDefaultKeycloak()
          .withRealmImportFiles("/camunda-foo-realm.json", "/camunda-bar-realm.json");

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

  @Test
  void shouldPrefixPickerLinksWithContextPathForPrimaryChain() {
    // given an app served under a servlet context path with two configured OIDC providers
    // when an unauthenticated request hits the context-relative login URL directly
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri(CONTEXT_PATH + "/login")
            .contextPath(CONTEXT_PATH)
            .accept(MediaType.TEXT_HTML)
            .exchange();

    // then the rendered picker lists both providers with context-path-prefixed hrefs, not the
    // unprefixed form that 404s under a context path
    assertThat(result).hasStatus(HttpStatus.OK);
    assertThat(result)
        .bodyText()
        .contains("href=\"" + CONTEXT_PATH + "/oauth2/authorization/foo\"")
        .contains("href=\"" + CONTEXT_PATH + "/oauth2/authorization/bar\"")
        .doesNotContain("href=\"/oauth2/authorization/foo\"")
        .doesNotContain("href=\"/oauth2/authorization/bar\"");
  }

  @Test
  void shouldPrefixPickerLinksWithContextAndScopePathForDefaultPhysicalTenantChain() {
    // given the implicit "default" physical-tenant scoped chain, which WebSecurityConfig wires
    // even with zero camunda.physical-tenants.* configured, inheriting the same two root providers
    // when an unauthenticated request hits that chain's own context-relative login URL
    final String scopedLoginPath = CONTEXT_PATH + "/physical-tenants/default/login";
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri(scopedLoginPath)
            .contextPath(CONTEXT_PATH)
            .accept(MediaType.TEXT_HTML)
            .exchange();

    // then the rendered picker's hrefs compose the context path (outer) with the scoped chain's
    // own /physical-tenants/default base path (inner), not either alone
    assertThat(result).hasStatus(HttpStatus.OK);
    assertThat(result)
        .bodyText()
        .contains("href=\"" + CONTEXT_PATH + "/physical-tenants/default/oauth2/authorization/foo\"")
        .contains("href=\"" + CONTEXT_PATH + "/physical-tenants/default/oauth2/authorization/bar\"")
        .doesNotContain("href=\"/physical-tenants/default/oauth2/authorization/foo\"")
        .doesNotContain("href=\"/physical-tenants/default/oauth2/authorization/bar\"");
  }
}

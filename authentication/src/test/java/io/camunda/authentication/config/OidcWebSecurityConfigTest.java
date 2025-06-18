/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.CamundaJwtAuthenticationConverter;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * This test covers the WebSecurityConfig behavior with an OIDC configuration.
 *
 * <p>It can be used to test all behavior except for the OIDC authentication flows (i.e. actually
 * authenticating against an external authentication server; validating JWTs from an authentication
 * server).
 *
 * <p>The test only uses no-op implementations and not meaningful configurations to configure the
 * OIDC client and token to authentication conversion (e.g. the properties for
 * camunda.security.authentication.oidc defined below are not meaningful, but required to start the
 * application context).
 *
 * <p>The test methods use Spring Security's {@link
 * SecurityMockMvcRequestPostProcessors#oidcLogin()} to circumvent actual token-based
 * authentication.
 *
 * <p>Without this (i.e. in the production setup), {@link BearerTokenAuthenticationFilter} converts
 * the JWT from the Authorization header into the {@link Authentication} object using {@link
 * CamundaJwtAuthenticationConverter}. It then adds it in the {@link SecurityContext} for further
 * access.
 *
 * <p>With this (i.e. in this test), we set the {@link Authentication} object already before
 * entering the filter chain (see AuthenticationRequestPostProcessor#postProcessRequest). {@link
 * BearerTokenAuthenticationFilter} is then skipped, because there is no token in the http headers
 * of the request.
 *
 * <p>Accordingly, you cannot write tests for the JWT handling with this setup.
 */
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityOidcTestContext.class,
      WebSecurityConfig.class
    },
    properties = {
      "logging.level.org.springframework.security=DEBUG",
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=example",
      "camunda.security.authentication.oidc.redirect-uri=redirect.example.com",
      "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=token.example.com",
      "camunda.security.authentication.oidc.jwk-set-uri=jwks.example.com"
    })
public class OidcWebSecurityConfigTest extends AbstractWebSecurityConfigTest {

  @ParameterizedTest
  @MethodSource("getAllDummyEndpoints")
  public void shouldAddSecurityHeadersOnAllApiAndWebappRequests(String endpoint) {

    // when
    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .uri(endpoint)
            .with(SecurityMockMvcRequestPostProcessors.oidcLogin())
            .exchange();

    // then
    assertThat(testResult).hasStatusOk();
    assertDefaultSecurityHeaders(testResult);
  }
}

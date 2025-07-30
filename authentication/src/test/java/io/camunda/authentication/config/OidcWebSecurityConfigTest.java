/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import io.camunda.authentication.config.controllers.OidcMockMvcTestHelper;
import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
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
 * org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter}.
 * It then adds it in the {@link SecurityContext} for further access.
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
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=example",
      "camunda.security.authentication.oidc.redirect-uri=redirect.example.com",
      "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=token.example.com",
      "camunda.security.authentication.oidc.jwk-set-uri=jwks.example.com"
    })
public class OidcWebSecurityConfigTest extends AbstractWebSecurityConfigTest {

  @Autowired private OAuth2AuthorizedClientRepository authorizedClientRepository;

  @ParameterizedTest
  @MethodSource("getAllDummyEndpoints")
  public void shouldAddSecurityHeadersOnAllApiAndWebappRequests(final String endpoint) {

    // when
    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .uri("https://localhost" + endpoint)
            .with(OidcMockMvcTestHelper.oidcLogin(authorizedClientRepository))
            .exchange();

    // then
    assertThat(testResult).hasStatusOk();
    assertDefaultSecurityHeaders(testResult);
  }

  @Test
  public void shouldRequireCsrfTokenWithSessionAuthentication() {
    // given
    final MockHttpSession mockHttpSession = new MockHttpSession();

    // when
    final MvcTestResult result =
        mockMvcTester
            .post()
            .session(mockHttpSession)
            .with(user("demo"))
            .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
            .exchange();

    // then
    assertMissingCsrfToken(result);
  }

  @Test
  public void shouldNotRequireCsrfTokenWithGetEndpoint() {
    // given
    final MockHttpSession mockHttpSession = new MockHttpSession();

    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .session(mockHttpSession)
            .with(user("demo"))
            .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatusOk();
  }

  @Test
  public void shouldSucceedWithCsrfTokenAndSessionAuthentication() {
    // given
    final MockHttpSession mockHttpSession = new MockHttpSession();

    // when
    final MvcTestResult result =
        mockMvcTester
            .post()
            .session(mockHttpSession)
            .with(user("demo"))
            .with(csrf())
            .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatusOk();
  }

  @Test
  public void shouldRejectRequestsToProtectedWebResourcesWithoutAuthentication() {
    // when
    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
            .exchange();

    // then
    assertThat(testResult).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldAcceptRequestToUnprotectedWebResourcesWithoutAuthentication() {
    // when
    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_UNPROTECTED_ENDPOINT)
            .exchange();

    // then
    assertThat(testResult).hasStatusOk();
  }

  @Test
  public void shouldReturnCsrfTokenOnAuthenticatedGetRequest() {
    // when
    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
            .with(OidcMockMvcTestHelper.oidcLogin(authorizedClientRepository))
            .exchange();

    // then
    assertThat(testResult)
        .hasStatusOk()
        .containsHeader(EXPECTED_CSRF_HEADER_NAME)
        .cookies()
        .containsCookie(EXPECTED_CSRF_TOKEN_COOKIE_NAME);

    final Cookie csrfCookie = testResult.getResponse().getCookie(EXPECTED_CSRF_TOKEN_COOKIE_NAME);

    assertThat(csrfCookie.isHttpOnly()).isTrue();
    assertThat(csrfCookie.getSecure()).isTrue();
  }

  @Test
  public void shouldNotSetSecureFlagOnCsrfCookieWhenUsingHttp() {
    // given
    final String webappUrl =
        "http://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT; // note: http - not https

    // when
    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .uri(webappUrl)
            .with(OidcMockMvcTestHelper.oidcLogin(authorizedClientRepository))
            .exchange();

    // then
    assertThat(testResult).cookies().containsCookie(EXPECTED_CSRF_TOKEN_COOKIE_NAME);

    final Cookie csrfCookie = testResult.getResponse().getCookie(EXPECTED_CSRF_TOKEN_COOKIE_NAME);

    assertThat(csrfCookie.isHttpOnly()).isTrue();
    assertThat(csrfCookie.getSecure()).isFalse();
  }
}

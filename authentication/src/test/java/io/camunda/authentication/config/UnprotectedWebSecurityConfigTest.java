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

import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.TestUserDetailsService;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.security.spring.security.CamundaSecurityFilterChainConstants;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(
    classes = {WebSecurityConfigTestContext.class, WebSecurityConfig.class},
    properties = {"camunda.security.authentication.unprotected-api=true"})
public class UnprotectedWebSecurityConfigTest extends AbstractWebSecurityConfigTest {

  @ParameterizedTest
  @MethodSource("getAllDummyEndpoints")
  public void shouldAddSecurityHeadersOnAllApiAndWebappRequests(final String endpoint) {

    // when
    final MvcTestResult testResult =
        mockMvcTester.get().uri("https://localhost" + endpoint).exchange();

    // then
    assertThat(testResult).hasStatusOk();
    assertDefaultSecurityHeaders(testResult);
  }

  @Test
  public void shouldRequireCsrfTokenWithSessionAuthentication() {
    // given: a real, cookie-backed session established via login — the unprotected API chain now
    // resolves the session from the camunda-session cookie (CSL ADR-0031), not a raw servlet
    // session, so CSRF protection keys off that cookie just like the protected chains.
    final var sessionCookie = logInAsDemoAndGetSessionCookie();

    // when
    final MvcTestResult result =
        mockMvcTester
            .post()
            .cookie(sessionCookie)
            .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
            .exchange();

    // then
    assertMissingCsrfToken(result);
  }

  private Cookie logInAsDemoAndGetSessionCookie() {
    final MvcTestResult loginResult =
        mockMvcTester
            .post()
            .uri("https://localhost/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .formField("username", TestUserDetailsService.DEMO_USERNAME)
            .formField("password", TestUserDetailsService.DEMO_USERNAME)
            .exchange();
    final Cookie sessionCookie =
        loginResult.getResponse().getCookie(CamundaSecurityFilterChainConstants.SESSION_COOKIE);
    assertThat(sessionCookie).isNotNull();
    return sessionCookie;
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
}

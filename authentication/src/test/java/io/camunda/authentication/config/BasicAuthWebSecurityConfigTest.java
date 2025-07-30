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
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=basic"
    })
public class BasicAuthWebSecurityConfigTest extends AbstractWebSecurityConfigTest {

  @ParameterizedTest
  @MethodSource("getAllDummyEndpoints")
  public void shouldAddSecurityHeadersOnAllApiAndWebappRequests(final String endpoint) {

    // when
    final MvcTestResult testResult =
        mockMvcTester.get().headers(basicAuthDemo()).uri("https://localhost" + endpoint).exchange();

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
  public void shouldAcceptRequestsToProtectedWebResourcesWithoutAuthentication() {
    // when
    final MvcTestResult testResult =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_WEBAPP_ENDPOINT)
            .exchange();

    // then
    assertThat(testResult).hasStatusOk();
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
  public void shouldReturnCsrfTokenOnSuccessfulLogin() {
    // when
    final MvcTestResult testResult =
        mockMvcTester
            .post()
            .uri("https://localhost/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .formField("username", TestUserDetailsService.DEMO_USERNAME)
            .formField("password", TestUserDetailsService.DEMO_USERNAME)
            .exchange();

    // then
    assertThat(testResult)
        .hasStatus(HttpStatus.NO_CONTENT)
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
    final String loginUrl = "http://localhost/login"; // note: http - not https

    // when
    final MvcTestResult testResult =
        mockMvcTester
            .post()
            .uri(loginUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .formField("username", TestUserDetailsService.DEMO_USERNAME)
            .formField("password", TestUserDetailsService.DEMO_USERNAME)
            .exchange();

    // then
    assertThat(testResult).cookies().containsCookie(EXPECTED_CSRF_TOKEN_COOKIE_NAME);

    final Cookie csrfCookie = testResult.getResponse().getCookie(EXPECTED_CSRF_TOKEN_COOKIE_NAME);

    assertThat(csrfCookie.isHttpOnly()).isTrue();
    assertThat(csrfCookie.getSecure()).isFalse();
  }

  protected static HttpHeaders basicAuthDemo() {
    final HttpHeaders headers = new HttpHeaders();

    headers.add(HttpHeaders.AUTHORIZATION, basicAuthentication("demo", "demo"));

    return headers;
  }

  private static String basicAuthentication(final String username, final String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }
}

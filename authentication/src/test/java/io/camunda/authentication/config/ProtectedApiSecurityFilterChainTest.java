/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Tests for the security filter chain behavior, specifically testing: - Valid /v2 endpoints with
 * authorized/unauthorized users - Invalid endpoints returning 404 (security by obscurity) -
 * Catch-all filter chain behavior
 */
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=basic"
    })
public class ProtectedApiSecurityFilterChainTest extends AbstractWebSecurityConfigTest {

  @Test
  public void shouldReturn200ForValidV2EndpointWithAuthorizedUser() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuthDemo())
            .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatusOk();
  }

  @Test
  public void shouldReturn401ForValidV2EndpointWithoutAuthentication() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldReturn401ForValidV2EndpointWithInvalidCredentials() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuthInvalid())
            .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldReturn404ForUnhandledApiEndpointWithoutAuthentication() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_UNHANDLED_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  public void shouldReturn404ForUnhandledApiEndpointWithValidAuthentication() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuthDemo())
            .uri("https://localhost" + TestApiController.DUMMY_UNHANDLED_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  public void shouldReturn404ForUnhandledApiEndpointWithInvalidAuthentication() {
    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(basicAuthInvalid())
            .uri("https://localhost" + TestApiController.DUMMY_UNHANDLED_ENDPOINT)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
  }

  private static HttpHeaders basicAuthDemo() {
    final HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, basicAuthentication("demo", "demo"));
    return headers;
  }

  private static HttpHeaders basicAuthInvalid() {
    final HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, basicAuthentication("invalid", "invalid"));
    return headers;
  }

  private static String basicAuthentication(final String username, final String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }
}

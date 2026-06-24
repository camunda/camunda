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
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
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
}

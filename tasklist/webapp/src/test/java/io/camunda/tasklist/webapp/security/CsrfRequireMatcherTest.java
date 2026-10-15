/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CsrfRequireMatcherTest {

  @Mock HttpServletRequest request;

  final CsrfRequireMatcher csrfRequireMatcher = new CsrfRequireMatcher();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldNotMatchSafeMethods() {
    when(request.getMethod()).thenReturn("GET");
    assertThat(csrfRequireMatcher.matches(request)).isFalse();
  }

  @Test
  void shouldNotMatchLogout() {
    when(request.getMethod()).thenReturn("POST");
    when(request.getServletPath()).thenReturn(LOGOUT_RESOURCE);
    assertThat(csrfRequireMatcher.matches(request)).isFalse();
  }

  @Test
  void shouldMatchLoginWithoutAuthentication() {
    // given a login request from a browser that is not logged in
    when(request.getMethod()).thenReturn("POST");
    when(request.getServletPath()).thenReturn(LOGIN_RESOURCE);

    // when / then
    assertThat(csrfRequireMatcher.matches(request)).isTrue();
  }

  @Test
  void shouldMatchLoginEvenWithBearerTokenAndSwaggerReferer() {
    // given a login request that would be exempt under the Swagger or Bearer rules
    authenticate();
    when(request.getMethod()).thenReturn("POST");
    when(request.getServletPath()).thenReturn(LOGIN_RESOURCE);
    lenient()
        .when(request.getHeader("Referer"))
        .thenReturn("http://localhost:8080/swagger-ui.html");
    lenient().when(request.getHeader("Authorization")).thenReturn("Bearer eyBlackCoffee");

    // when / then
    assertThat(csrfRequireMatcher.matches(request)).isTrue();
  }

  @Test
  void shouldMatchAuthenticatedApiRequest() {
    authenticate();
    when(request.getMethod()).thenReturn("POST");
    when(request.getServletPath()).thenReturn("/v1/tasks/search");
    assertThat(csrfRequireMatcher.matches(request)).isTrue();
  }

  @Test
  void shouldNotMatchAuthenticatedRequestWithBearerToken() {
    authenticate();
    when(request.getMethod()).thenReturn("POST");
    when(request.getServletPath()).thenReturn("/v1/tasks/search");
    when(request.getHeader("Referer")).thenReturn(null);
    when(request.getHeader("Authorization")).thenReturn("Bearer eyBlackCoffee");
    assertThat(csrfRequireMatcher.matches(request)).isFalse();
  }

  private static void authenticate() {
    final var authentication = new TestingAuthenticationToken("demo", "demo", "ROLE_USER");
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}

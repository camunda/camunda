/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CsrfRequireMatcherTest {

  @Mock HttpServletRequest request;

  final CsrfRequireMatcher csrfRequireMatcher = new CsrfRequireMatcher();

  @Test
  void shouldNotMatchHttpMethods() {
    Stream.of("GET", "HEAD", "TRACE", "OPTIONS")
        .forEach(
            method -> {
              when(request.getMethod()).thenReturn(method);
              assertThat(csrfRequireMatcher.matches(request)).isFalse();
            });
  }

  @Test
  void shouldNotMatchPaths() {
    when(request.getMethod()).thenReturn("POST");
    Stream.of(LOGIN_RESOURCE, LOGOUT_RESOURCE)
        .forEach(
            path -> {
              when(request.getServletPath()).thenReturn(path);
              assertThat(csrfRequireMatcher.matches(request)).isFalse();
            });
  }

  @Test
  void shouldNotMatchForSwagger() {
    when(request.getMethod()).thenReturn("POST");
    when(request.getServletPath()).thenReturn("/swagger-ui.html");
    when(request.getHeader("Referer")).thenReturn("http://localhost:8080/swagger-ui.html");
    when(request.getRequestURL())
        .thenReturn(new StringBuffer("http://localhost:8080/swagger-ui.html"));
    assertThat(csrfRequireMatcher.matches(request)).isFalse();
  }

  @Test
  void shouldNotMatchForPublicAPIAccessWithBearerToken() {
    when(request.getMethod()).thenReturn("POST");
    when(request.getServletPath()).thenReturn("/v1/process-definitions/search");
    when(request.getHeader("Referer")).thenReturn(null);
    when(request.getRequestURL())
        .thenReturn(new StringBuffer("http://localhost:8080/v1/process-definitions/search"));
    when(request.getHeader("Authorization")).thenReturn("Bearer eyBlackCoffee");
    assertThat(csrfRequireMatcher.matches(request)).isFalse();
  }

  @Test
  void shouldMatchForInternalAPIAccess() {
    when(request.getMethod()).thenReturn("POST");
    when(request.getServletPath()).thenReturn("/api/processes/grouped");
    when(request.getHeader("Referer")).thenReturn(null);
    when(request.getRequestURL())
        .thenReturn(new StringBuffer("http://localhost:8080//api/processes/grouped"));
    assertThat(csrfRequireMatcher.matches(request)).isTrue();
  }
}

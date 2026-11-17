/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class OptimizeOidcAuthenticationEntryPointTest {

  private final OptimizeOidcAuthenticationEntryPoint entryPoint =
      new OptimizeOidcAuthenticationEntryPoint("/login");

  @Test
  void shouldReturnUnauthorizedForApiSubPath() throws Exception {
    assertThat(commence("/api/dashboard/1").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void shouldReturnUnauthorizedForExactApiPath() throws Exception {
    // "/api" (no trailing slash) is part of the API surface, matching Spring's "/api/**".
    assertThat(commence("/api").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void shouldRedirectToLoginForNavigation() throws Exception {
    final MockHttpServletResponse response = commence("/dashboards");
    assertThat(response.getStatus()).isEqualTo(HttpStatus.FOUND.value());
    assertThat(response.getRedirectedUrl()).endsWith("/login");
  }

  private MockHttpServletResponse commence(final String uri) throws IOException, ServletException {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    entryPoint.commence(request, response, new BadCredentialsException("unauthenticated"));
    return response;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class CsrfProtectionRequestMatcherTest {

  private final CsrfProtectionRequestMatcher matcher =
      new CsrfProtectionRequestMatcher(
          Set.of("/actuator/**", "/error"), Set.of("/v2/license"), "/login", "/logout");

  @Test
  void shouldExemptSafeHttpMethods() {
    // given
    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/some-path");
    request.setServletPath("/some-path");

    // when
    var result = matcher.matches(request);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldExemptConfiguredUnprotectedPaths() {
    // given
    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/actuator/health");
    request.setServletPath("/actuator/health");
    request.getSession(true);

    // when
    var result = matcher.matches(request);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldExemptNonBrowserApiCalls() {
    // given
    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/some-path");
    request.setServletPath("/some-path");
    // no session — non-browser API call

    // when
    var result = matcher.matches(request);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldMatchBrowserPostRequest() {
    // given
    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/some-path");
    request.setServletPath("/some-path");
    request.getSession(true);

    // when
    var result = matcher.matches(request);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldExemptSwaggerUiRequests() {
    // given
    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/v3/api-docs");
    request.setServletPath("/v3/api-docs");
    request.addHeader("Referer", "http://localhost/swagger-ui/index.html");
    request.getSession(true);

    // when
    var result = matcher.matches(request);

    // then
    assertThat(result).isFalse();
  }
}

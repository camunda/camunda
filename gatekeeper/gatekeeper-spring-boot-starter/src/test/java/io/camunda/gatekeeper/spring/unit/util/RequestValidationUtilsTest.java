/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.spring.util.RequestValidationUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestValidationUtilsTest {

  @Test
  void shouldAllowRedirectToSameHost() {
    final var request = createRequest();
    assertThat(RequestValidationUtils.isAllowedRedirect(request, "http://localhost/some/page"))
        .isTrue();
  }

  @Test
  void shouldRejectNullUrl() {
    final var request = createRequest();
    assertThat(RequestValidationUtils.isAllowedRedirect(request, null)).isFalse();
  }

  @Test
  void shouldRejectBlankUrl() {
    final var request = createRequest();
    assertThat(RequestValidationUtils.isAllowedRedirect(request, "   ")).isFalse();
  }

  @Test
  void shouldRejectUrlWithCarriageReturn() {
    final var request = createRequest();
    assertThat(RequestValidationUtils.isAllowedRedirect(request, "http://localhost\r/evil"))
        .isFalse();
  }

  @Test
  void shouldRejectUrlWithNewline() {
    final var request = createRequest();
    assertThat(RequestValidationUtils.isAllowedRedirect(request, "http://localhost\n/evil"))
        .isFalse();
  }

  @Test
  void shouldRejectDifferentHost() {
    final var request = createRequest();
    assertThat(RequestValidationUtils.isAllowedRedirect(request, "http://evil.com/page")).isFalse();
  }

  @Test
  void shouldAllowRedirectWithPathAndQuery() {
    final var request = createRequest();
    assertThat(RequestValidationUtils.isAllowedRedirect(request, "http://localhost/page?foo=bar"))
        .isTrue();
  }

  private MockHttpServletRequest createRequest() {
    final var request = new MockHttpServletRequest();
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(80);
    request.setRequestURI("/");
    return request;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.spring.csrf.CsrfProtectionRequestMatcher;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class CsrfProtectionRequestMatcherTest {

  private final CsrfProtectionRequestMatcher matcher =
      new CsrfProtectionRequestMatcher(Set.of("/login", "/logout", "/v1/**"));

  @Test
  void shouldNotMatchGetRequests() {
    final var request = createRequest("GET", "/api/data");
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void shouldNotMatchHeadRequests() {
    final var request = createRequest("HEAD", "/api/data");
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void shouldNotMatchOptionsRequests() {
    final var request = createRequest("OPTIONS", "/api/data");
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void shouldNotMatchAllowedPaths() {
    final var request = createRequest("POST", "/login");
    request.setSession(new MockHttpSession());
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void shouldNotMatchAllowedWildcardPaths() {
    final var request = createRequest("POST", "/v1/some/endpoint");
    request.setSession(new MockHttpSession());
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void shouldMatchPostFromBrowserWithSession() {
    final var request = createRequest("POST", "/api/data");
    request.setSession(new MockHttpSession());
    assertThat(matcher.matches(request)).isTrue();
  }

  @Test
  void shouldNotMatchPostWithoutSession() {
    final var request = createRequest("POST", "/api/data");
    // No session set — getSession(false) returns null
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void shouldNotMatchSwaggerUiReferer() {
    final var request = createRequest("POST", "/api/data");
    request.setSession(new MockHttpSession());
    request.addHeader("Referer", "http://localhost/swagger-ui/index.html");
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void shouldNotMatchSwaggerUiRefererWithExtraPath() {
    final var request = createRequest("POST", "/api/data");
    request.setSession(new MockHttpSession());
    request.setServerName("hel-1.operate.ultrawombat.com");
    request.setScheme("https");
    request.setServerPort(443);
    request.addHeader(
        "Referer",
        "https://hel-1.operate.ultrawombat.com/00000000-0000-0000-0000-000000000000/swagger-ui/index.html");
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void shouldNotMatchSwaggerUiRefererWithExtraPathAndPort() {
    final var request = createRequest("POST", "/api/data");
    request.setSession(new MockHttpSession());
    request.setServerName("hel-1.operate.ultrawombat.com");
    request.setScheme("https");
    request.setServerPort(12345);
    request.addHeader(
        "Referer",
        "https://hel-1.operate.ultrawombat.com:12345/00000000-0000-0000-0000-000000000000/swagger-ui/index.html");
    assertThat(matcher.matches(request)).isFalse();
  }

  private MockHttpServletRequest createRequest(final String method, final String servletPath) {
    final var request = new MockHttpServletRequest(method, servletPath);
    request.setServletPath(servletPath);
    request.setServerName("localhost");
    request.setServerPort(80);
    request.setScheme("http");
    return request;
  }
}

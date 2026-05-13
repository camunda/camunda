/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WebAppProviderAdapterTest {

  private final WebAppProviderAdapter provider = new WebAppProviderAdapter();

  @Test
  void shouldResolveOperateFromPath() {
    assertThat(provider.webAppFor(get("/operate/processes/123"))).contains("operate");
  }

  @Test
  void shouldResolveTasklistFromPath() {
    assertThat(provider.webAppFor(get("/tasklist/foo"))).contains("tasklist");
  }

  @Test
  void shouldResolveAdminFromPath() {
    assertThat(provider.webAppFor(get("/admin/dashboard"))).contains("admin");
  }

  @Test
  void shouldReturnEmptyForWebappPath() {
    assertThat(provider.webAppFor(get("/webapp/anything"))).isEmpty();
  }

  @Test
  void shouldReturnEmptyForRoot() {
    assertThat(provider.webAppFor(get("/"))).isEmpty();
  }

  @Test
  void shouldReturnEmptyForUnknownPrefix() {
    assertThat(provider.webAppFor(get("/api/v2/foo"))).isEmpty();
  }

  @Test
  void shouldResolveOperateWhenContextPathIsSet() {
    assertThat(provider.webAppFor(get("/camunda", "/camunda/operate/dashboard")))
        .contains("operate");
  }

  @Test
  void shouldResolveTasklistWhenContextPathIsSet() {
    assertThat(provider.webAppFor(get("/camunda", "/camunda/tasklist/foo"))).contains("tasklist");
  }

  @Test
  void shouldReturnEmptyForUnknownPrefixWhenContextPathIsSet() {
    assertThat(provider.webAppFor(get("/camunda", "/camunda/api/v2/foo"))).isEmpty();
  }

  private static HttpServletRequest get(final String path) {
    final var request = new MockHttpServletRequest("GET", path);
    request.setRequestURI(path);
    return request;
  }

  private static HttpServletRequest get(final String contextPath, final String requestUri) {
    final var request = new MockHttpServletRequest("GET", requestUri);
    request.setContextPath(contextPath);
    request.setRequestURI(requestUri);
    return request;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer.CookieValue;

class PhysicalTenantCookieSerializerTest {

  @Test
  void shouldScopePrefixedTenantCookieToTenantPath() {
    final var serializer = PhysicalTenantCookieSerializer.forPrefixedChain("tenanta");
    final var response = new MockHttpServletResponse();
    serializer.writeCookieValue(new CookieValue(new MockHttpServletRequest(), response, "raw"));
    final Cookie cookie = response.getCookie("camunda-session-tenanta");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getPath()).isEqualTo("/physical-tenant/tenanta");
  }

  @Test
  void shouldScopeUnprefixedDefaultCookieToRoot() {
    final var serializer = PhysicalTenantCookieSerializer.forUnprefixedDefaultChain();
    final var response = new MockHttpServletResponse();
    serializer.writeCookieValue(new CookieValue(new MockHttpServletRequest(), response, "raw"));
    final Cookie cookie = response.getCookie("camunda-session-default-root");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getPath()).isEqualTo("/");
  }
}

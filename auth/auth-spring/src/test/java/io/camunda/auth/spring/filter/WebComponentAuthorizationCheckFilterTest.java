/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.domain.spi.WebComponentAccessProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class WebComponentAuthorizationCheckFilterTest {

  @Mock private CamundaAuthenticationProvider authenticationProvider;
  @Mock private WebComponentAccessProvider webComponentAccessProvider;
  @Mock private FilterChain filterChain;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldAllowStaticResources() throws Exception {
    // given
    var filter =
        new WebComponentAuthorizationCheckFilter(
            authenticationProvider, webComponentAccessProvider);
    var request = new MockHttpServletRequest();
    request.setRequestURI("/operate/static/main.css");
    request.setServletPath("/operate/static/main.css");
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    assertThat(response.getRedirectedUrl()).isNull();
  }

  @Test
  void shouldAllowWhenAuthorizationDisabled() throws Exception {
    // given
    when(webComponentAccessProvider.isAuthorizationEnabled()).thenReturn(false);
    setAuthenticatedUser();
    var filter =
        new WebComponentAuthorizationCheckFilter(
            authenticationProvider, webComponentAccessProvider);
    var request = new MockHttpServletRequest();
    request.setRequestURI("/operate/dashboard");
    request.setServletPath("/operate/dashboard");
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    assertThat(response.getRedirectedUrl()).isNull();
  }

  @Test
  void shouldAllowWhenNotAuthenticated() throws Exception {
    // given
    SecurityContextHolder.clearContext();
    var filter =
        new WebComponentAuthorizationCheckFilter(
            authenticationProvider, webComponentAccessProvider);
    var request = new MockHttpServletRequest();
    request.setRequestURI("/operate/dashboard");
    request.setServletPath("/operate/dashboard");
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    assertThat(response.getRedirectedUrl()).isNull();
  }

  @Test
  void shouldDenyUnauthorizedWebComponent() throws Exception {
    // given
    when(webComponentAccessProvider.isAuthorizationEnabled()).thenReturn(true);
    var camundaAuth = CamundaAuthentication.of(b -> b.user("user"));
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(camundaAuth);
    when(webComponentAccessProvider.hasAccessToComponent(camundaAuth, "operate")).thenReturn(false);
    setAuthenticatedUser();
    var filter =
        new WebComponentAuthorizationCheckFilter(
            authenticationProvider, webComponentAccessProvider);
    var request = new MockHttpServletRequest();
    request.setRequestURI("/operate/dashboard");
    request.setServletPath("/operate/dashboard");
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    assertThat(response.getRedirectedUrl()).isEqualTo("/operate/forbidden");
  }

  @Test
  void shouldAllowAuthorizedWebComponent() throws Exception {
    // given
    when(webComponentAccessProvider.isAuthorizationEnabled()).thenReturn(true);
    var camundaAuth = CamundaAuthentication.of(b -> b.user("user"));
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(camundaAuth);
    when(webComponentAccessProvider.hasAccessToComponent(camundaAuth, "operate")).thenReturn(true);
    setAuthenticatedUser();
    var filter =
        new WebComponentAuthorizationCheckFilter(
            authenticationProvider, webComponentAccessProvider);
    var request = new MockHttpServletRequest();
    request.setRequestURI("/operate/dashboard");
    request.setServletPath("/operate/dashboard");
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    assertThat(response.getRedirectedUrl()).isNull();
  }

  private void setAuthenticatedUser() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "pass", "ROLE_USER"));
  }
}

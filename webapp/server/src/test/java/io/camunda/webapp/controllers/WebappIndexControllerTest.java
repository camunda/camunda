/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.REQUESTED_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
class WebappIndexControllerTest {

  @Mock private ServletContext servletContext;
  @Mock private HttpServletRequest request;

  @Test
  void shouldReturnWebappIndexView() {
    // given
    when(servletContext.getContextPath()).thenReturn("/camunda");
    final WebappIndexController controller = new WebappIndexController(servletContext, false);
    final ExtendedModelMap model = new ExtendedModelMap();

    // when
    final String viewName = controller.webapp(model);

    // then
    assertThat(viewName).isEqualTo("webapp/index");
    assertThat(model.getAttribute("contextPath")).isEqualTo("/camunda/webapp/");
  }

  @Test
  void shouldForwardToWebappWhenLoginNotDelegated() {
    // given
    final WebappIndexController controller = new WebappIndexController(servletContext, false);

    // when
    final String result = controller.forwardToWebapp(request);

    // then no auth check is performed; result is unconditional forward to /webapp
    assertThat(result).isEqualTo("forward:/webapp");
  }

  @Test
  void shouldForwardToWebappWhenLoginDelegatedAndUserAuthenticated() {
    // given
    final WebappIndexController controller = new WebappIndexController(servletContext, true);
    final Authentication authentication = mock(Authentication.class);
    when(authentication.isAuthenticated()).thenReturn(true);

    try (final MockedStatic<SecurityContextHolder> mocked =
        mockStatic(SecurityContextHolder.class)) {
      final SecurityContext securityContext = mock(SecurityContext.class);
      when(securityContext.getAuthentication()).thenReturn(authentication);
      mocked.when(SecurityContextHolder::getContext).thenReturn(securityContext);

      // when
      final String result = controller.forwardToWebapp(request);

      // then
      assertThat(result).isEqualTo("forward:/webapp");
    }
  }

  @Test
  void shouldForwardToLoginAndStashUrlWhenLoginDelegatedAndUserUnauthenticated() {
    // given
    final WebappIndexController controller = new WebappIndexController(servletContext, true);
    final HttpSession session = mock(HttpSession.class);
    final AnonymousAuthenticationToken anonymous = mock(AnonymousAuthenticationToken.class);

    when(request.getRequestURI()).thenReturn("/webapp/foo/bar");
    when(request.getContextPath()).thenReturn("");
    when(request.getSession(true)).thenReturn(session);

    try (final MockedStatic<SecurityContextHolder> mocked =
        mockStatic(SecurityContextHolder.class)) {
      final SecurityContext securityContext = mock(SecurityContext.class);
      when(securityContext.getAuthentication()).thenReturn(anonymous);
      mocked.when(SecurityContextHolder::getContext).thenReturn(securityContext);

      // when
      final String result = controller.forwardToWebapp(request);

      // then the URL is stashed in the session and the request is forwarded to /login
      assertThat(result).isEqualTo("forward:/login");
      verify(session).setAttribute(REQUESTED_URL, "/webapp/foo/bar");
    }
  }
}

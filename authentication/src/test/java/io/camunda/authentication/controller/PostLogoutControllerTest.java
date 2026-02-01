/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PostLogoutControllerTest {

  private static final String POST_LOGOUT_REDIRECT_ATTRIBUTE = "postLogoutRedirect";

  private final PostLogoutController controller = new PostLogoutController();

  @Mock private HttpServletRequest request;
  @Mock private HttpSession session;

  @Test
  void shouldRedirectToDefaultWhenSessionIsNotPresent() {
    // given
    when(request.getSession(false)).thenReturn(null);

    // when
    final String postLogoutRedirectUrl = controller.postLogout(request);

    // then
    assertThat(postLogoutRedirectUrl).isEqualTo("redirect:/");
  }

  @Test
  void shouldFallbackToDefaultWhenNonStringAttribute() {
    // given a session with a non-string postLogoutRedirect attribute
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(POST_LOGOUT_REDIRECT_ATTRIBUTE)).thenReturn(111);

    // when
    final String postLogoutRedirectUrl = controller.postLogout(request);

    // then
    assertThat(postLogoutRedirectUrl).isEqualTo("redirect:/");
  }

  @Test
  void shouldFallbackToDefaultWhenBlankStringAttribute() {
    // given a session with a blank postLogoutRedirect attribute
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(POST_LOGOUT_REDIRECT_ATTRIBUTE)).thenReturn("   ");

    // when
    final String postLogoutRedirectUrl = controller.postLogout(request);

    // then
    assertThat(postLogoutRedirectUrl).isEqualTo("redirect:/");
  }

  @Test
  void shouldFallbackToDefaultWhenAttributeMissing() {
    // given
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(POST_LOGOUT_REDIRECT_ATTRIBUTE)).thenReturn(null);

    // when
    final String postLogoutRedirectUrl = controller.postLogout(request);

    // then
    assertThat(postLogoutRedirectUrl).isEqualTo("redirect:/");
  }

  @Test
  void shouldRedirectToPostLogoutRedirectWhenPresent() {
    // given a session with a valid postLogoutRedirect attribute
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(POST_LOGOUT_REDIRECT_ATTRIBUTE)).thenReturn("/after-logout");

    // when
    final String postLogoutRedirectUrl = controller.postLogout(request);

    // then
    assertThat(postLogoutRedirectUrl).isEqualTo("redirect:/after-logout");
  }
}

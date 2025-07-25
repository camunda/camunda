/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.holder;

import static io.camunda.authentication.holder.HttpSessionBasedAuthenticationHolder.CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class HttpSessionBasedAuthenticationHolderTest {

  @Mock private HttpServletRequest request;
  private HttpSessionBasedAuthenticationHolder holder;

  @BeforeEach
  void setup() {
    request = mock(HttpServletRequest.class);
    holder = new HttpSessionBasedAuthenticationHolder(request);
  }

  @Test
  public void shouldSupportWhenSessionExists() {
    // given
    final var session = mock(HttpSession.class);
    when(request.getSession(eq(false))).thenReturn(session);

    // when
    final var result = holder.supports();

    // then
    assertThat(result).isTrue();
  }

  @Test
  public void shouldNotSupportWhenSessionDoesNotExist() {
    // given
    when(request.getSession(eq(false))).thenReturn(null);

    // when
    final var result = holder.supports();

    // then
    assertThat(result).isFalse();
  }

  @Test
  public void shouldReturnAuthentication() {
    // given
    final var authentication = mock(CamundaAuthentication.class);
    final var session = mock(HttpSession.class);

    when(request.getSession(eq(false))).thenReturn(session);
    when(session.getAttribute(eq(CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY)))
        .thenReturn(authentication);

    // when
    final var result = holder.get();

    // then
    assertThat(result).isEqualTo(authentication);
  }

  @Test
  public void shouldAddAuthenticationToSession() {
    // given
    final var authentication = mock(CamundaAuthentication.class);
    final var session = mock(HttpSession.class);

    when(request.getSession(eq(false))).thenReturn(session);

    // when
    holder.set(authentication);

    // then
    verify(session, times(1))
        .setAttribute(eq(CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY), eq(authentication));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.holder;

import static io.camunda.authentication.holder.RequestContextBasedAuthenticationHolder.CAMUNDA_AUTHENTICATION_REQUEST_HOLDER_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

import io.camunda.security.auth.CamundaAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public class RequestContextBasedAuthenticationHolderTest {

  @Mock private RequestAttributes requestAttributes;
  @Mock private HttpServletRequest request;
  private RequestContextBasedAuthenticationHolder holder;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    RequestContextHolder.setRequestAttributes(requestAttributes);
    request = mock(HttpServletRequest.class);
    holder = new RequestContextBasedAuthenticationHolder(request);
  }

  @Test
  public void shouldSupportWhenSessionExists() {
    // given
    when(request.getSession(eq(false))).thenReturn(null);

    // when
    final var result = holder.supports();

    // then
    assertThat(result).isTrue();
  }

  @Test
  public void shouldNotSupportWhenSessionDoesNotExist() {
    // given
    final var session = mock(HttpSession.class);
    when(request.getSession(eq(false))).thenReturn(session);

    // when
    final var result = holder.supports();

    // then
    assertThat(result).isFalse();
  }

  @Test
  public void shouldReturnAuthentication() {
    // given
    final var authentication = mock(CamundaAuthentication.class);
    when(requestAttributes.getAttribute(
            eq(CAMUNDA_AUTHENTICATION_REQUEST_HOLDER_KEY), eq(SCOPE_REQUEST)))
        .thenReturn(authentication);

    // when
    final var result = holder.get();

    // then
    assertThat(result).isEqualTo(authentication);
  }

  @Test
  public void shouldAddAuthenticationToRequest() {
    // given
    final var authentication = mock(CamundaAuthentication.class);

    // when
    holder.set(authentication);

    // then
    verify(requestAttributes, times(1))
        .setAttribute(
            eq(CAMUNDA_AUTHENTICATION_REQUEST_HOLDER_KEY), eq(authentication), eq(SCOPE_REQUEST));
  }
}

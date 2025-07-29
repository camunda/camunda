/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class DefaultCamundaAuthenticationProviderTest {

  @Mock CamundaAuthenticationHolder holder;
  @Mock private CamundaAuthenticationConverter<Authentication> authenticationConverter;
  private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    authenticationConverter = mock(CamundaAuthenticationConverter.class);
    holder = mock(CamundaAuthenticationHolder.class);
    authenticationProvider =
        new DefaultCamundaAuthenticationProvider(holder, authenticationConverter);
  }

  @Test
  void shouldReturnAuthenticationFromHolder() {
    // given
    final var expectedAuthentication = CamundaAuthentication.of(b -> b.user("foo"));

    final var mockAuthentication = mock(Authentication.class);
    when(mockAuthentication.getPrincipal()).thenReturn("foo");
    SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
    when(holder.get()).thenReturn(expectedAuthentication);

    // when
    final var actualAuthentication = authenticationProvider.getCamundaAuthentication();

    // then
    assertThat(actualAuthentication).isNotNull();
    assertThat(actualAuthentication).isEqualTo(expectedAuthentication);
  }

  @Test
  void shouldConvertAndHoldAuthentication() {
    // given
    final var expectedAuthentication = CamundaAuthentication.of(b -> b.user("foo"));

    final var mockAuthentication = mock(Authentication.class);
    when(mockAuthentication.getPrincipal()).thenReturn("foo");
    SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
    when(authenticationConverter.convert(eq(mockAuthentication)))
        .thenReturn(expectedAuthentication);

    // when
    final var actualAuthentication = authenticationProvider.getCamundaAuthentication();

    // then
    assertThat(actualAuthentication).isNotNull();
    assertThat(actualAuthentication).isEqualTo(expectedAuthentication);
    verify(holder).set(eq(expectedAuthentication));
  }

  @Test
  void shouldConvertButNotCacheIfAnonymous() {
    // given
    final var expectedAuthentication = CamundaAuthentication.anonymous();

    final var mockAuthentication = mock(Authentication.class);
    when(mockAuthentication.getPrincipal()).thenReturn("foo");
    SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
    when(authenticationConverter.convert(eq(mockAuthentication)))
        .thenReturn(expectedAuthentication);

    // when
    final var actualAuthentication = authenticationProvider.getCamundaAuthentication();

    // then
    assertThat(actualAuthentication).isNotNull();
    assertThat(actualAuthentication).isEqualTo(expectedAuthentication);
    verify(holder, times(0)).set(eq(expectedAuthentication));
  }
}

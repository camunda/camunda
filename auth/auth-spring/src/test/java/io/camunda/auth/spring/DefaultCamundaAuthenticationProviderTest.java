/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.CamundaAuthenticationConverter;
import io.camunda.auth.spring.holder.CamundaAuthenticationHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class DefaultCamundaAuthenticationProviderTest {

  @Mock private CamundaAuthenticationHolder holder;
  @Mock private CamundaAuthenticationConverter<Authentication> converter;

  private DefaultCamundaAuthenticationProvider provider;

  @BeforeEach
  void setUp() {
    provider = new DefaultCamundaAuthenticationProvider(holder, converter);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldReturnCachedAuthenticationFromHolder() {
    // given
    final var cachedAuth = new CamundaAuthentication.Builder().user("cached-user").build();
    when(holder.supports()).thenReturn(true);
    when(holder.get()).thenReturn(cachedAuth);

    // when
    final CamundaAuthentication result = provider.getCamundaAuthentication();

    // then
    assertThat(result).isEqualTo(cachedAuth);
    verify(converter, never()).supports(any());
  }

  @Test
  void shouldConvertAndCacheWhenHolderReturnsNull() {
    // given
    final Authentication springAuth = new TestingAuthenticationToken("user", "pass");
    springAuth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(springAuth);

    final var converted = new CamundaAuthentication.Builder().user("user").build();
    when(holder.supports()).thenReturn(true);
    when(holder.get()).thenReturn(null);
    when(converter.supports(springAuth)).thenReturn(true);
    when(converter.convert(springAuth)).thenReturn(converted);

    // when
    final CamundaAuthentication result = provider.getCamundaAuthentication();

    // then
    assertThat(result).isEqualTo(converted);
    verify(holder).set(converted);
  }

  @Test
  void shouldReturnAnonymousWhenNoSpringAuthentication() {
    // given
    SecurityContextHolder.clearContext();
    when(holder.supports()).thenReturn(true);
    when(holder.get()).thenReturn(null);

    // when
    final CamundaAuthentication result = provider.getCamundaAuthentication();

    // then
    assertThat(result.anonymousUser()).isTrue();
  }

  @Test
  void shouldReturnAnonymousWhenConverterDoesNotSupport() {
    // given
    final Authentication springAuth = new TestingAuthenticationToken("user", "pass");
    springAuth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(springAuth);

    when(holder.supports()).thenReturn(false);
    when(converter.supports(springAuth)).thenReturn(false);

    // when
    final CamundaAuthentication result = provider.getCamundaAuthentication();

    // then
    assertThat(result.anonymousUser()).isTrue();
  }

  @Test
  void shouldSkipHolderCachingWhenHolderDoesNotSupport() {
    // given
    final Authentication springAuth = new TestingAuthenticationToken("user", "pass");
    springAuth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(springAuth);

    final var converted = new CamundaAuthentication.Builder().user("user").build();
    when(holder.supports()).thenReturn(false);
    when(converter.supports(springAuth)).thenReturn(true);
    when(converter.convert(springAuth)).thenReturn(converted);

    // when
    final CamundaAuthentication result = provider.getCamundaAuthentication();

    // then
    assertThat(result).isEqualTo(converted);
    verify(holder, never()).set(any());
  }
}

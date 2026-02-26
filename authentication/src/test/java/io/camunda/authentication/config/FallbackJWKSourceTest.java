/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackJWKSourceTest {

  @SuppressWarnings("unchecked")
  private final JWKSource<SecurityContext> primarySource = mock(JWKSource.class);

  @SuppressWarnings("unchecked")
  private final JWKSource<SecurityContext> secondarySource = mock(JWKSource.class);

  private final JWKSelector selector = mock(JWKSelector.class);

  @Test
  void shouldReturnKeysFromPrimarySource() throws KeySourceException {
    // given
    final var expectedKey = mock(JWK.class);
    when(primarySource.get(any(), any())).thenReturn(List.of(expectedKey));
    final var fallbackSource = new FallbackJWKSource(List.of(primarySource, secondarySource));

    // when
    final var result = fallbackSource.get(selector, null);

    // then
    assertThat(result).containsExactly(expectedKey);
    verify(secondarySource, never()).get(any(), any());
  }

  @Test
  void shouldFallBackToSecondaryWhenPrimaryReturnsEmpty() throws KeySourceException {
    // given
    final var expectedKey = mock(JWK.class);
    when(primarySource.get(any(), any())).thenReturn(List.of());
    when(secondarySource.get(any(), any())).thenReturn(List.of(expectedKey));
    final var fallbackSource = new FallbackJWKSource(List.of(primarySource, secondarySource));

    // when
    final var result = fallbackSource.get(selector, null);

    // then
    assertThat(result).containsExactly(expectedKey);
  }

  @Test
  void shouldFallBackToSecondaryWhenPrimaryThrows() throws KeySourceException {
    // given
    final var expectedKey = mock(JWK.class);
    when(primarySource.get(any(), any())).thenThrow(new KeySourceException("connection failed"));
    when(secondarySource.get(any(), any())).thenReturn(List.of(expectedKey));
    final var fallbackSource = new FallbackJWKSource(List.of(primarySource, secondarySource));

    // when
    final var result = fallbackSource.get(selector, null);

    // then
    assertThat(result).containsExactly(expectedKey);
  }

  @Test
  void shouldReturnEmptyWhenAllSourcesReturnEmpty() throws KeySourceException {
    // given
    when(primarySource.get(any(), any())).thenReturn(List.of());
    when(secondarySource.get(any(), any())).thenReturn(List.of());
    final var fallbackSource = new FallbackJWKSource(List.of(primarySource, secondarySource));

    // when
    final var result = fallbackSource.get(selector, null);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenAllSourcesFail() throws KeySourceException {
    // given
    when(primarySource.get(any(), any())).thenThrow(new KeySourceException("primary failed"));
    when(secondarySource.get(any(), any())).thenThrow(new KeySourceException("secondary failed"));
    final var fallbackSource = new FallbackJWKSource(List.of(primarySource, secondarySource));

    // when
    final var result = fallbackSource.get(selector, null);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectEmptySourceList() {
    assertThatThrownBy(() -> new FallbackJWKSource(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullSourceList() {
    assertThatThrownBy(() -> new FallbackJWKSource(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

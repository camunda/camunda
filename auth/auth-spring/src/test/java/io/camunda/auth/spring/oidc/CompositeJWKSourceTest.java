/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeJWKSourceTest {

  @Mock private JWKSelector jwkSelector;

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnKeysFromFirstNonEmptySource() throws Exception {
    // given
    final JWKSource<SecurityContext> source1 = mock(JWKSource.class);
    final JWKSource<SecurityContext> source2 = mock(JWKSource.class);
    final JWK mockJwk = mock(JWK.class);
    when(source1.get(any(), any())).thenReturn(List.of());
    when(source2.get(any(), any())).thenReturn(List.of(mockJwk));

    final var composite = new CompositeJWKSource<>(List.of(source1, source2));

    // when
    final var keys = composite.get(jwkSelector, null);

    // then
    assertThat(keys).containsExactly(mockJwk);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnKeysFromFirstSourceWhenNonEmpty() throws Exception {
    // given
    final JWKSource<SecurityContext> source1 = mock(JWKSource.class);
    final JWKSource<SecurityContext> source2 = mock(JWKSource.class);
    final JWK mockJwk = mock(JWK.class);
    when(source1.get(any(), any())).thenReturn(List.of(mockJwk));

    final var composite = new CompositeJWKSource<>(List.of(source1, source2));

    // when
    final var keys = composite.get(jwkSelector, null);

    // then
    assertThat(keys).containsExactly(mockJwk);
    verify(source2, never()).get(any(), any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldFallThroughOnException() throws Exception {
    // given
    final JWKSource<SecurityContext> source1 = mock(JWKSource.class);
    final JWKSource<SecurityContext> source2 = mock(JWKSource.class);
    final JWK mockJwk = mock(JWK.class);
    when(source1.get(any(), any())).thenThrow(new KeySourceException("source1 failed"));
    when(source2.get(any(), any())).thenReturn(List.of(mockJwk));

    final var composite = new CompositeJWKSource<>(List.of(source1, source2));

    // when
    final var keys = composite.get(jwkSelector, null);

    // then
    assertThat(keys).containsExactly(mockJwk);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldRethrowLastExceptionWhenAllFail() throws Exception {
    // given
    final JWKSource<SecurityContext> source1 = mock(JWKSource.class);
    final JWKSource<SecurityContext> source2 = mock(JWKSource.class);
    when(source1.get(any(), any())).thenThrow(new KeySourceException("source1 failed"));
    when(source2.get(any(), any())).thenThrow(new KeySourceException("source2 failed"));

    final var composite = new CompositeJWKSource<>(List.of(source1, source2));

    // when/then
    assertThatThrownBy(() -> composite.get(jwkSelector, null))
        .isInstanceOf(KeySourceException.class)
        .hasMessage("source2 failed");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnEmptyWhenAllEmpty() throws Exception {
    // given
    final JWKSource<SecurityContext> source1 = mock(JWKSource.class);
    final JWKSource<SecurityContext> source2 = mock(JWKSource.class);
    when(source1.get(any(), any())).thenReturn(List.of());
    when(source2.get(any(), any())).thenReturn(List.of());

    final var composite = new CompositeJWKSource<>(List.of(source1, source2));

    // when
    final var keys = composite.get(jwkSelector, null);

    // then
    assertThat(keys).isEmpty();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.camunda.gatekeeper.spring.oidc.CompositeJWKSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class CompositeJWKSourceTest {

  @Mock private JWKSource<SecurityContext> source1;
  @Mock private JWKSource<SecurityContext> source2;
  @Mock private JWKSelector jwkSelector;
  @Mock private JWK jwk;

  @Test
  void shouldReturnKeysFromFirstSourceWhenAvailable() throws KeySourceException {
    // given
    when(source1.get(eq(jwkSelector), any())).thenReturn(List.of(jwk));
    final var composite = new CompositeJWKSource<>(List.of(source1, source2));

    // when
    final var result = composite.get(jwkSelector, null);

    // then
    assertThat(result).containsExactly(jwk);
  }

  @Test
  void shouldFallbackToSecondSourceWhenFirstReturnsEmpty() throws KeySourceException {
    // given
    when(source1.get(eq(jwkSelector), any())).thenReturn(List.of());
    when(source2.get(eq(jwkSelector), any())).thenReturn(List.of(jwk));
    final var composite = new CompositeJWKSource<>(List.of(source1, source2));

    // when
    final var result = composite.get(jwkSelector, null);

    // then
    assertThat(result).containsExactly(jwk);
  }

  @Test
  void shouldFallbackToSecondSourceWhenFirstThrowsException() throws KeySourceException {
    // given
    when(source1.get(eq(jwkSelector), any())).thenThrow(new KeySourceException("source1 failed"));
    when(source2.get(eq(jwkSelector), any())).thenReturn(List.of(jwk));
    final var composite = new CompositeJWKSource<>(List.of(source1, source2));

    // when
    final var result = composite.get(jwkSelector, null);

    // then
    assertThat(result).containsExactly(jwk);
  }

  @Test
  void shouldRethrowLastExceptionWhenAllSourcesFail() throws KeySourceException {
    // given
    when(source1.get(eq(jwkSelector), any())).thenThrow(new KeySourceException("source1 failed"));
    when(source2.get(eq(jwkSelector), any())).thenThrow(new KeySourceException("source2 failed"));
    final var composite = new CompositeJWKSource<>(List.of(source1, source2));

    // when / then
    assertThatThrownBy(() -> composite.get(jwkSelector, null))
        .isInstanceOf(KeySourceException.class)
        .hasMessage("source2 failed");
  }

  @Test
  void shouldReturnEmptyListWhenAllSourcesReturnEmpty() throws KeySourceException {
    // given
    when(source1.get(eq(jwkSelector), any())).thenReturn(List.of());
    when(source2.get(eq(jwkSelector), any())).thenReturn(List.of());
    final var composite = new CompositeJWKSource<>(List.of(source1, source2));

    // when
    final var result = composite.get(jwkSelector, null);

    // then
    assertThat(result).isEmpty();
  }
}

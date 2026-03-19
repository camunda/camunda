/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.camunda.gatekeeper.exception.GatekeeperAuthenticationException;
import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.spi.CamundaAuthenticationConverter;
import io.camunda.gatekeeper.spring.converter.CamundaAuthenticationDelegatingConverter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
final class CamundaAuthenticationDelegatingConverterTest {

  @Mock private CamundaAuthenticationConverter<Authentication> converter1;
  @Mock private CamundaAuthenticationConverter<Authentication> converter2;

  @Test
  void shouldAlwaysReturnTrueForSupports() {
    // given
    final var delegating = new CamundaAuthenticationDelegatingConverter(List.of());

    // when / then
    assertThat(delegating.supports(null)).isTrue();
  }

  @Test
  void shouldDelegateToFirstSupportingConverter() {
    // given
    final var token = new UsernamePasswordAuthenticationToken("alice", "password");
    final var expectedAuth = CamundaAuthentication.of(b -> b.user("alice"));
    when(converter1.supports(token)).thenReturn(false);
    when(converter2.supports(token)).thenReturn(true);
    when(converter2.convert(token)).thenReturn(expectedAuth);
    final var delegating =
        new CamundaAuthenticationDelegatingConverter(List.of(converter1, converter2));

    // when
    final var result = delegating.convert(token);

    // then
    assertThat(result).isEqualTo(expectedAuth);
  }

  @Test
  void shouldThrowWhenNoConverterSupportsAuthentication() {
    // given
    final var token = new UsernamePasswordAuthenticationToken("alice", "password");
    when(converter1.supports(token)).thenReturn(false);
    final var delegating = new CamundaAuthenticationDelegatingConverter(List.of(converter1));

    // when / then
    assertThatThrownBy(() -> delegating.convert(token))
        .isInstanceOf(GatekeeperAuthenticationException.class)
        .hasMessageContaining("Did not find a matching converter");
  }

  @Test
  void shouldThrowWithNullClassNameWhenAuthenticationIsNull() {
    // given
    final var delegating = new CamundaAuthenticationDelegatingConverter(List.of(converter1));
    when(converter1.supports(null)).thenReturn(false);

    // when / then
    assertThatThrownBy(() -> delegating.convert(null))
        .isInstanceOf(GatekeeperAuthenticationException.class)
        .hasMessageContaining("null");
  }
}

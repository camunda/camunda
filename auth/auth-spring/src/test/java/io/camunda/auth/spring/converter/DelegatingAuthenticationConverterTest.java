/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.CamundaAuthenticationConverter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class DelegatingAuthenticationConverterTest {

  @Mock private CamundaAuthenticationConverter<Authentication> converter1;
  @Mock private CamundaAuthenticationConverter<Authentication> converter2;

  @Test
  void shouldDelegateToFirstSupportingConverter() {
    // given
    final Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
    final var expected = new CamundaAuthentication.Builder().user("user").build();

    when(converter1.supports(auth)).thenReturn(false);
    when(converter2.supports(auth)).thenReturn(true);
    when(converter2.convert(auth)).thenReturn(expected);

    final var delegating = new DelegatingAuthenticationConverter(List.of(converter1, converter2));

    // when
    final CamundaAuthentication result = delegating.convert(auth);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldSupportWhenAnyConverterSupports() {
    // given
    final Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
    when(converter1.supports(auth)).thenReturn(false);
    when(converter2.supports(auth)).thenReturn(true);

    final var delegating = new DelegatingAuthenticationConverter(List.of(converter1, converter2));

    // then
    assertThat(delegating.supports(auth)).isTrue();
  }

  @Test
  void shouldNotSupportWhenNoConverterSupports() {
    // given
    final Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
    when(converter1.supports(auth)).thenReturn(false);
    when(converter2.supports(auth)).thenReturn(false);

    final var delegating = new DelegatingAuthenticationConverter(List.of(converter1, converter2));

    // then
    assertThat(delegating.supports(auth)).isFalse();
  }

  @Test
  void shouldThrowWhenNoConverterSupportsConvert() {
    // given
    final Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
    when(converter1.supports(auth)).thenReturn(false);
    when(converter2.supports(auth)).thenReturn(false);

    final var delegating = new DelegatingAuthenticationConverter(List.of(converter1, converter2));

    // when/then
    assertThatThrownBy(() -> delegating.convert(auth))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No converter found");
  }
}

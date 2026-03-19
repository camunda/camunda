/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.spi.CamundaAuthenticationConverter;
import io.camunda.gatekeeper.spi.CamundaAuthenticationHolder;
import io.camunda.gatekeeper.spring.DefaultCamundaAuthenticationProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
final class DefaultCamundaAuthenticationProviderTest {

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
  void shouldReturnFromHolderWhenPresent() {
    // given
    final var springAuth = new UsernamePasswordAuthenticationToken("alice", "password");
    SecurityContextHolder.getContext().setAuthentication(springAuth);
    final var cachedAuth = CamundaAuthentication.of(b -> b.user("alice"));
    when(holder.get()).thenReturn(cachedAuth);

    // when
    final var result = provider.getCamundaAuthentication();

    // then
    assertThat(result).isEqualTo(cachedAuth);
    verify(converter, never()).convert(springAuth);
  }

  @Test
  void shouldConvertAndCacheWhenNotInHolder() {
    // given
    final var springAuth = new UsernamePasswordAuthenticationToken("bob", "password");
    SecurityContextHolder.getContext().setAuthentication(springAuth);
    when(holder.get()).thenReturn(null);
    final var convertedAuth = CamundaAuthentication.of(b -> b.user("bob").role("viewer"));
    when(converter.convert(springAuth)).thenReturn(convertedAuth);

    // when
    final var result = provider.getCamundaAuthentication();

    // then
    assertThat(result).isEqualTo(convertedAuth);
    verify(holder).set(convertedAuth);
  }

  @Test
  void shouldNotCacheAnonymousAuthentication() {
    // given
    SecurityContextHolder.getContext().setAuthentication(null);
    final var anonymousAuth = CamundaAuthentication.anonymous();
    when(converter.convert(null)).thenReturn(anonymousAuth);

    // when
    final var result = provider.getCamundaAuthentication();

    // then
    assertThat(result.isAnonymous()).isTrue();
    verify(holder, never()).set(anonymousAuth);
  }
}

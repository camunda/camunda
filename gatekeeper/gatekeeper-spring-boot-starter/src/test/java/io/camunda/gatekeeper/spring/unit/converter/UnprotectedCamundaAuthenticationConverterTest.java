/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.spring.converter.UnprotectedCamundaAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

final class UnprotectedCamundaAuthenticationConverterTest {

  private final UnprotectedCamundaAuthenticationConverter converter =
      new UnprotectedCamundaAuthenticationConverter();

  @Test
  void shouldSupportNullAuthentication() {
    assertThat(converter.supports(null)).isTrue();
  }

  @Test
  void shouldSupportAnonymousAuthenticationToken() {
    // given
    final var token =
        new AnonymousAuthenticationToken(
            "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    // when / then
    assertThat(converter.supports(token)).isTrue();
  }

  @Test
  void shouldNotSupportUsernamePasswordToken() {
    // given
    final var token = new UsernamePasswordAuthenticationToken("alice", "password");

    // when / then
    assertThat(converter.supports(token)).isFalse();
  }

  @Test
  void shouldReturnAnonymousAuthentication() {
    // when
    final var result = converter.convert(null);

    // then
    assertThat(result).isNotNull();
    assertThat(result.isAnonymous()).isTrue();
  }
}

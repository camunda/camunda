/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.auth.domain.exception.TokenExchangeException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DelegationChainValidatorTest {

  private final DelegationChainValidator validator = new DelegationChainValidator(2);

  @Test
  void shouldAcceptTokenWithNoActClaim() {
    // given
    final String jwt = createJwt("{\"sub\":\"user-1\",\"aud\":\"service-a\"}");

    // when/then
    assertThatNoException().isThrownBy(() -> validator.validate(jwt));
  }

  @Test
  void shouldAcceptTokenWithSingleActClaim() {
    // given
    final String jwt =
        createJwt(
            "{\"sub\":\"user-1\",\"act\":{\"sub\":\"service-a\"},\"aud\":\"service-b\"}");

    // when/then
    assertThatNoException().isThrownBy(() -> validator.validate(jwt));
  }

  @Test
  void shouldRejectTokenWithTooManyActClaims() {
    // given
    final String jwt =
        createJwt(
            "{\"sub\":\"user-1\",\"act\":{\"sub\":\"service-a\",\"act\":{\"sub\":\"service-b\"}}}");

    // when/then
    assertThatThrownBy(() -> validator.validate(jwt))
        .isInstanceOf(TokenExchangeException.DelegationChainTooDeep.class)
        .hasMessageContaining("exceeds maximum");
  }

  @Test
  void shouldAcceptNullToken() {
    // given/when/then
    assertThatNoException().isThrownBy(() -> validator.validate(null));
  }

  @Test
  void shouldAcceptInvalidJwt() {
    // given — not a valid JWT, should gracefully return depth 0
    // when/then
    assertThatNoException().isThrownBy(() -> validator.validate("not-a-jwt"));
  }

  @Test
  void shouldCountActClaimDepthCorrectly() {
    // given
    final String noAct = createJwt("{\"sub\":\"user-1\"}");
    final String oneAct = createJwt("{\"sub\":\"user-1\",\"act\":{\"sub\":\"svc-a\"}}");

    // when/then
    assertThat(validator.countActClaimDepth(noAct)).isZero();
    assertThat(validator.countActClaimDepth(oneAct)).isEqualTo(1);
  }

  @Test
  void shouldRejectMaxDepthLessThanOne() {
    // when/then
    assertThatThrownBy(() -> new DelegationChainValidator(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private String createJwt(final String payload) {
    final String header =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"alg\":\"RS256\"}".getBytes());
    final String body =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
    return header + "." + body + ".signature";
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.unit.model.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class AuthenticationMethodTest {

  @Test
  void shouldParseBasicLowercase() {
    assertThat(AuthenticationMethod.parse("basic"))
        .isEqualTo(Optional.of(AuthenticationMethod.BASIC));
  }

  @Test
  void shouldParseBasicUppercase() {
    assertThat(AuthenticationMethod.parse("BASIC"))
        .isEqualTo(Optional.of(AuthenticationMethod.BASIC));
  }

  @Test
  void shouldParseBasicMixedCase() {
    assertThat(AuthenticationMethod.parse("Basic"))
        .isEqualTo(Optional.of(AuthenticationMethod.BASIC));
  }

  @Test
  void shouldParseOidcLowercase() {
    assertThat(AuthenticationMethod.parse("oidc"))
        .isEqualTo(Optional.of(AuthenticationMethod.OIDC));
  }

  @Test
  void shouldParseOidcUppercase() {
    assertThat(AuthenticationMethod.parse("OIDC"))
        .isEqualTo(Optional.of(AuthenticationMethod.OIDC));
  }

  @Test
  void shouldReturnEmptyForNull() {
    assertThat(AuthenticationMethod.parse(null)).isEmpty();
  }

  @Test
  void shouldThrowForUnknownValue() {
    assertThatThrownBy(() -> AuthenticationMethod.parse("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported authentication method: unknown");
  }
}

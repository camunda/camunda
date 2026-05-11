/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import org.junit.jupiter.api.Test;

final class SecretEvaluationContextTest {

  private final SecretEvaluationContext context = new SecretEvaluationContext();

  @Test
  void shouldReturnLiteralReferenceForKnownSecretName() {
    // when
    final var result = context.getVariable("MY_SECRET");

    // then — the leaf lookup is a terminal value (Left), and it carries the namespace-prefixed
    // reference string instead of the real secret.
    assertThat(result.isLeft()).isTrue();
    assertThat(MsgPackConverter.convertToJson(result.getLeft()))
        .isEqualTo("\"camunda.secret.MY_SECRET\"");
  }

  @Test
  void shouldRoundTripAnyIdentifierStyleName() {
    final var snake = context.getVariable("snake_case_value");
    final var leading = context.getVariable("_LEADING");
    final var mixed = context.getVariable("MixedCase42");

    assertThat(MsgPackConverter.convertToJson(snake.getLeft()))
        .isEqualTo("\"camunda.secret.snake_case_value\"");
    assertThat(MsgPackConverter.convertToJson(leading.getLeft()))
        .isEqualTo("\"camunda.secret._LEADING\"");
    assertThat(MsgPackConverter.convertToJson(mixed.getLeft()))
        .isEqualTo("\"camunda.secret.MixedCase42\"");
  }

  @Test
  void shouldReturnLeftNullForEmptyName() {
    // FEEL never asks with an empty segment, but defend against it anyway — Left(null) means
    // "no value at this name" per the EvaluationContext contract.
    final var result = context.getVariable("");

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isNull();
  }

  @Test
  void shouldReturnLeftNullForNullName() {
    final var result = context.getVariable(null);

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isNull();
  }

  @Test
  void shouldBeProcessScopeAgnostic() {
    // The literal-reference behavior is the same regardless of which process instance asks.
    assertThat(context.processScoped(42L)).isSameAs(context);
  }

  @Test
  void shouldBeTenantScopeAgnostic() {
    assertThat(context.tenantScoped("tenant-a")).isSameAs(context);
  }

  @Test
  void shouldNotMaterializeSecretValueEvenIfNameLooksLikeOne() {
    // The context never consults a SecretStore. Even names that happen to match env vars in the
    // test JVM resolve to their literal reference, not the underlying value.
    final var anyEnvVarName = "PATH";

    final var result = context.getVariable(anyEnvVarName);

    assertThat(MsgPackConverter.convertToJson(result.getLeft()))
        .isEqualTo("\"camunda.secret.PATH\"");
  }
}

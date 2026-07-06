/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SecretReferenceTest {

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("sourcesWithReferences")
  void shouldParseSecretReferencesUsedAsExpression(
      final String source, final Set<SecretReference> expected) {
    // when
    final var references = SecretReference.parse(source);

    // then
    assertThat(references).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @NullSource
  @ValueSource(
      strings = {
        // not an expression (does not start with '='): the whole value is a literal
        "",
        "camunda.secrets.token",
        // a secret reference used inside a string literal stays a literal (secret-injection safe)
        "=\"camunda.secrets.token\"",
        "=\"the secret is camunda.secrets.token\"",
        // 'camunda' is not the root variable
        "=xcamunda.secrets.token",
        "=order.camunda.secrets.token",
        // wrong or incomplete reference format
        "=camunda.vars.clusterVariable",
        "=camunda.secrets.",
        "=camunda.secrets",
        // no reference at all
        "=",
        "=userId + orderId",
        "=\"only literal text\"",
        // static value (no '='): the whole thing is a literal
        "\"Bearer \" + camunda.secrets.X",
        // reference inside a concatenated string literal stays literal
        "=\"Bearer \" + \"camunda.secrets.X\""
      })
  void shouldReturnEmptyWhenNoSecretReferenceUsedAsExpression(final String source) {
    // when / then
    assertThat(SecretReference.parse(source)).isEmpty();
  }

  @Test
  void shouldDeduplicateRepeatedReferences() {
    // when
    final var references =
        SecretReference.parse("=camunda.secrets.token + \"-\" + camunda.secrets.token");

    // then
    assertThat(references).containsExactly(new SecretReference("token"));
  }

  @Test
  void shouldParseReferenceOutsideButNotInsideStringLiteralWithEscapedQuote() {
    // given - a string literal containing an escaped quote and a reference to ignore, followed by a
    // real reference used as an expression
    final var source = "=\"a\\\"b camunda.secrets.ignored\" + camunda.secrets.real";

    // when
    final var references = SecretReference.parse(source);

    // then
    assertThat(references).containsExactly(new SecretReference("real"));
  }

  @Test
  void shouldExposeFullReference() {
    // when / then
    assertThat(new SecretReference("token").reference()).isEqualTo("camunda.secrets.token");
  }

  static Stream<Arguments> sourcesWithReferences() {
    return Stream.of(
        arguments("=camunda.secrets.token", refs("token")),
        arguments("=\"Bearer \" + camunda.secrets.token", refs("token")),
        arguments(
            "=\"Bearer \" + camunda.secrets.token + camunda.secrets.postfix",
            refs("token", "postfix")),
        arguments("=camunda.secrets.MY_SECRET_2", refs("MY_SECRET_2")),
        arguments("=camunda.secrets._underscore", refs("_underscore")),
        // a literal reference is ignored, but the expression reference in the same source is parsed
        arguments("=\"camunda.secrets.literal\" + camunda.secrets.real", refs("real")),
        // reference nested inside a FEEL context value
        arguments("={ token: camunda.secrets.token }", refs("token")),
        // a literal reference is ignored, a real reference after it is captured
        arguments("=\"Bearer \" + \"camunda.secrets.X\" + camunda.secrets.Y", refs("Y")));
  }

  private static Set<SecretReference> refs(final String... names) {
    return Arrays.stream(names).map(SecretReference::new).collect(Collectors.toSet());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference.DetectedSecret;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SecretReferenceTest {

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("sourcesWithReferences")
  void shouldParseSecretReferencesUsedAsExpression(
      final String source, final Set<SecretReference> expected) {
    // when / then
    assertThat(referencesIn(source)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{index}] {0}")
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
        // a reference commented out is not part of the parsed expression
        "=1 // camunda.secrets.token",
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
    assertThat(referencesIn(source)).isEmpty();
  }

  @Test
  void shouldReturnEmptyForNullExpression() {
    // when / then
    assertThat(SecretReference.parse(null)).isEmpty();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(
      strings = {
        // 'camunda' is bound by the iteration, yet the reference is still reported
        "=for camunda in [1, 2] return camunda.secrets.token",
        "=some camunda in [1, 2] satisfies camunda.secrets.token = 1",
        "=every camunda in [1, 2] satisfies camunda.secrets.token = 1"
      })
  void shouldStillReportReferenceWhenRootIsShadowedByIteration(final String source) {
    // documented limitation: feel-scala suppresses only whole-name matches, so a bound 'camunda'
    // does not shadow a qualified camunda.secrets.<name> path; the reference is still reported
    assertThat(referencesIn(source)).containsExactly(new SecretReference("token"));
  }

  @Test
  void shouldDeduplicateRepeatedReferences() {
    // when
    final var references = referencesIn("=camunda.secrets.token + \"-\" + camunda.secrets.token");

    // then
    assertThat(references).containsExactly(new SecretReference("token"));
  }

  @Test
  void shouldParseReferenceOutsideButNotInsideStringLiteralWithEscapedQuote() {
    // given - a string literal containing an escaped quote and a reference to ignore, followed by a
    // real reference used as an expression
    final var source = "=\"a\\\"b camunda.secrets.ignored\" + camunda.secrets.real";

    // when / then
    assertThat(referencesIn(source)).containsExactly(new SecretReference("real"));
  }

  @Test
  void shouldParseReferenceAfterStringLiteralEndingInEscapedBackslash() {
    // given - the literal ends in an escaped backslash; the reference after it is still detected
    final var source = "=\"a\\\\\" + camunda.secrets.real";

    // when / then
    assertThat(referencesIn(source)).containsExactly(new SecretReference("real"));
  }

  @Test
  void shouldReportContextPathForNestedReferences() {
    // given - a FEEL context with references at different depths and a literal to ignore
    final var source =
        "={a: camunda.secrets.x, b: \"camunda.secrets.y\", c: {d: camunda.secrets.z}}";

    // when
    final var located = SecretReference.parse(expressionLanguage.parseExpression(source));

    // then - the literal 'y' is ignored; each reference carries the keys of its enclosing context
    assertThat(located)
        .extracting(DetectedSecret::path, DetectedSecret::secret)
        .containsExactlyInAnyOrder(
            tuple(List.of("a"), new SecretReference("x")),
            tuple(List.of("c", "d"), new SecretReference("z")));
  }

  @Test
  void shouldReportEmptyContextPathForScalarSource() {
    // given
    final var source = "=\"Bearer \" + camunda.secrets.token";

    // when
    final var located = SecretReference.parse(expressionLanguage.parseExpression(source));

    // then
    assertThat(located).extracting(DetectedSecret::path).containsExactly(List.of());
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
        // whitespace and line breaks around the dots are insignificant in FEEL
        arguments("=camunda . secrets . token", refs("token")),
        arguments("=camunda\n  .secrets\n  .token", refs("token")),
        // unicode names are valid FEEL identifiers
        arguments("=camunda.secrets.tokén", refs("tokén")),
        // backtick-escaped names allow special characters
        arguments("=camunda.secrets.`my-secret`", refs("my-secret")),
        // trailing path access after the secret name still references the secret
        arguments("=camunda.secrets.token.length", refs("token")),
        // a reference used inside a comment is not part of the expression
        arguments("=camunda.secrets.token // camunda.secrets.other", refs("token")),
        // a literal reference is ignored, but the expression reference in the same source is parsed
        arguments("=\"camunda.secrets.literal\" + camunda.secrets.real", refs("real")),
        // reference nested inside a FEEL context value
        arguments("={ token: camunda.secrets.token }", refs("token")),
        // a bigger context with several references, some literal
        arguments(
            "={a: camunda.secrets.x, b: \"literal\", c: {d: camunda.secrets.y}}", refs("x", "y")),
        // a literal reference is ignored, a real reference after it is captured
        arguments("=\"Bearer \" + \"camunda.secrets.X\" + camunda.secrets.Y", refs("Y")));
  }

  private Set<SecretReference> referencesIn(final String source) {
    return SecretReference.parse(expressionLanguage.parseExpression(source)).stream()
        .map(DetectedSecret::secret)
        .collect(Collectors.toSet());
  }

  private static Set<SecretReference> refs(final String... names) {
    return Arrays.stream(names).map(SecretReference::new).collect(Collectors.toSet());
  }
}

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
        // a trailing path access into the secret is not a reference (longer qualified name)
        "=camunda.secrets.token.length",
        // no reference at all
        "=",
        "=userId + orderId",
        "=\"only literal text\"",
        // static value (no '='): the whole thing is a literal
        "\"Bearer \" + camunda.secrets.X",
        // reference inside a concatenated string literal stays literal
        "=\"Bearer \" + \"camunda.secrets.X\"",
        // a FEEL context holds no reference: a string-literal entry, plain-variable entries, empty
        "={x: \"camunda.secrets.token\"}",
        "={x: userId, y: orderId}",
        "={}"
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

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(
      strings = {
        // case A: a secret reference inside a FEEL list literal
        "=[camunda.secrets.x]",
        "=[camunda.secrets.x, camunda.secrets.y]",
        "=[1, camunda.secrets.x]",
        // case B: a secret reference inside a context literal produced by a branch, not the root
        "=if true then {x: camunda.secrets.token} else null",
        "=if true then null else {x: camunda.secrets.token}",
        // nested if/then/else, reference several branches deep
        "=if a then (if b then {x: camunda.secrets.token} else null) else null",
        // a list nested inside a context produced by a branch
        "=if a then {x: [camunda.secrets.token]} else null",
        // a list nested inside a root context (the list itself is still not descended)
        "={x: [camunda.secrets.token]}",
        // mixed: the then-branch alone is imprecise, so the whole conditional still is
        "=if c then {x: camunda.secrets.a} else camunda.secrets.b"
      })
  void shouldReportImpreciseReference(final String source) {
    // when / then
    assertThat(SecretReference.hasImpreciseReference(expressionLanguage.parseExpression(source)))
        .isTrue();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(
      strings = {
        // a plain scalar reference
        "=camunda.secrets.token",
        // embedded in a string via concatenation - still a single scalar leaf
        "=\"Bearer \" + camunda.secrets.token",
        // a context literal is descended precisely, at the root and nested
        "={x: camunda.secrets.token}",
        "={x: {y: camunda.secrets.token}}",
        // a list or conditional with no secret reference inside is not flagged
        "=[1, 2, 3]",
        "=if true then 1 else 2",
        // no reference at all
        "=userId + orderId",
        // not an expression: the whole value is a literal, parse() already returns nothing for it
        "camunda.secrets.token",
        // container and secret in different branches: the secret branch is leaf-precise and the
        // container branch leaves no placeholder
        "=if useDefault then {x: \"literal\"} else camunda.secrets.token",
        // same, list variant
        "=if useDefault then [1, 2] else camunda.secrets.token",
        // the secret is only in the condition, so no placeholder ever reaches the mapping target
        "=if camunda.secrets.flag = \"on\" then {x: 1} else null"
      })
  void shouldNotReportImpreciseReference(final String source) {
    // when / then
    assertThat(SecretReference.hasImpreciseReference(expressionLanguage.parseExpression(source)))
        .isFalse();
  }

  @Test
  void shouldNotReportImpreciseReferenceForNullExpression() {
    // when / then
    assertThat(SecretReference.hasImpreciseReference(null)).isFalse();
  }

  @Test
  void shouldReportBothBranchesOfAConditionalAtTheEnclosingPath() {
    // given - FEEL takes one branch, but detection cannot know which, so both are recorded
    final var source = "=if cond then camunda.secrets.x else camunda.secrets.y";

    // when
    final var located = SecretReference.parse(expressionLanguage.parseExpression(source));

    // then - both at the empty path, i.e. the mapping target's own leaf
    assertThat(located)
        .extracting(DetectedSecret::path, DetectedSecret::secret)
        .containsExactlyInAnyOrder(
            tuple(List.of(), new SecretReference("x")), tuple(List.of(), new SecretReference("y")));
  }

  @Test
  void shouldReportEveryBranchOfNestedConditionals() {
    // given
    final var source =
        "=if a then (if b then camunda.secrets.p else camunda.secrets.q) else camunda.secrets.r";

    // when / then
    assertThat(referencesIn(source))
        .containsExactlyInAnyOrder(
            new SecretReference("p"), new SecretReference("q"), new SecretReference("r"));
  }

  @Test
  void shouldReportConditionalNestedInsideConcatenation() {
    // given
    final var source =
        "=\"Bearer \" + (if prod then camunda.secrets.prodT else camunda.secrets.devT)";

    // when / then
    assertThat(referencesIn(source))
        .containsExactlyInAnyOrder(new SecretReference("prodT"), new SecretReference("devT"));
  }

  @Test
  void shouldReportOnlyTheSecretBranchWhenTheOtherIsALiteral() {
    // given
    final var source = "=if true then \"localSecret\" else camunda.secrets.prod";

    // when / then
    assertThat(referencesIn(source)).containsExactly(new SecretReference("prod"));
  }

  @Test
  void shouldReportSecretUsedOnlyInAConditionalCondition() {
    // given - the secret is compared, never mapped, but it is still a reference
    final var source = "=if camunda.secrets.flag = \"on\" then \"a\" else \"b\"";

    // when / then
    assertThat(referencesIn(source)).containsExactly(new SecretReference("flag"));
  }

  @Test
  void shouldDeduplicateTheSameSecretInBothBranches() {
    // given - one reference, so injection always finds its placeholder
    final var source = "=if c then camunda.secrets.t else camunda.secrets.t";

    // when / then
    assertThat(referencesIn(source)).containsExactly(new SecretReference("t"));
  }

  @Test
  void shouldReportSecretWrappedInAFunctionCall() {
    // given - the reference is detected even though the function will mangle the placeholder
    // beyond matching at injection
    final var source = "=upper case(camunda.secrets.token)";

    // when / then
    assertThat(referencesIn(source)).containsExactly(new SecretReference("token"));
  }

  @Test
  void shouldReportSecretInsideAListLiteralAtTheEnclosingPath() {
    // given - a list literal is not descended, so the reference lands on the enclosing path
    final var source = "=[camunda.secrets.X, camunda.secrets.Y]";

    // when
    final var located = SecretReference.parse(expressionLanguage.parseExpression(source));

    // then
    assertThat(located)
        .extracting(DetectedSecret::path, DetectedSecret::secret)
        .containsExactlyInAnyOrder(
            tuple(List.of(), new SecretReference("X")), tuple(List.of(), new SecretReference("Y")));
  }

  @Test
  void shouldReportSecretBehindAListIndexAtTheEnclosingPath() {
    // given - indexing a list literal yields a text leaf at runtime, so this resolves fine
    final var source = "=[camunda.secrets.x][1]";

    // when
    final var located = SecretReference.parse(expressionLanguage.parseExpression(source));

    // then
    assertThat(located)
        .extracting(DetectedSecret::path, DetectedSecret::secret)
        .containsExactly(tuple(List.of(), new SecretReference("x")));
  }

  @Test
  void shouldReportSecretInsideAForReturnAtTheEnclosingPath() {
    // given - produces a list at runtime, which injection cannot address
    final var source = "=for i in [1, 2] return camunda.secrets.token";

    // when
    final var located = SecretReference.parse(expressionLanguage.parseExpression(source));

    // then
    assertThat(located)
        .extracting(DetectedSecret::path, DetectedSecret::secret)
        .containsExactly(tuple(List.of(), new SecretReference("token")));
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
        // backticks escape a dot too, so this is a three-segment name 'tls.crt' rather than the
        // four-segment path the unquoted camunda.secrets.tls.crt is. The detector reports it and
        // the engine resolves it from the store, while SecretServices' charset rejects the same
        // name on /v2/secrets/resolve — the #60364 mismatch, one charset over
        arguments("=camunda.secrets.`tls.crt`", refs("tls.crt")),
        // written bare, a dash is FEEL's minus operator, so the source parses as
        // camunda.secrets.my - secret and only 'my' is reported. Backticks are the way to write a
        // dashed name, and the evaluation of the subtraction fails afterwards
        arguments("=camunda.secrets.my-secret", refs("my")),
        // a reference used inside a comment is not part of the expression
        arguments("=camunda.secrets.token // camunda.secrets.other", refs("token")),
        // a literal reference is ignored
        arguments("=\"camunda.secrets.literal\"", Set.of()),
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

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
import io.camunda.zeebe.engine.processing.deployment.model.element.ClusterVariableReference.DetectedClusterVariable;
import java.time.InstantSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClusterVariableReferenceTest {

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("sourcesWithReferences")
  void shouldParseClusterVariableReferencesUsedAsExpression(
      final String source, final Set<ClusterVariableReference> expected) {
    // when / then
    assertThat(referencesIn(source)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(
      strings = {
        // not an expression (does not start with '='): the whole value is a literal
        "",
        "camunda.vars.env.myVar",
        // a cluster-variable reference used inside a string literal stays a literal
        "=\"camunda.vars.env.myVar\"",
        // 'camunda' is not the root variable
        "=xcamunda.vars.env.myVar",
        "=order.camunda.vars.env.myVar",
        // a reference commented out is not part of the parsed expression
        "=1 // camunda.vars.env.myVar",
        // wrong or incomplete reference format: only 3 segments, no variable name
        "=camunda.vars.env",
        "=camunda.vars.",
        "=camunda.vars",
        // 'processInstance' is not a whitelisted scope
        "=camunda.vars.processInstance.x",
        // a secret reference is not a cluster-variable reference (detectors are disjoint)
        "=camunda.secrets.token",
        // no reference at all
        "=",
        "=userId + orderId",
        "=\"only literal text\"",
        // static value (no '='): the whole thing is a literal
        "\"camunda.vars.env.myVar\"",
        // a FEEL context holds no reference
        "={x: \"camunda.vars.env.myVar\"}",
        "={x: userId, y: orderId}",
        "={}"
      })
  void shouldReturnEmptyWhenNoClusterVariableReferenceUsedAsExpression(final String source) {
    // when / then
    assertThat(referencesIn(source)).isEmpty();
  }

  @Test
  void shouldReturnEmptyForNullExpression() {
    // when / then
    assertThat(ClusterVariableReference.parse(null)).isEmpty();
  }

  @Test
  void shouldReportRepeatedReferenceOnce() {
    // feel-scala's variable references are already de-duplicated, so a reference used twice in one
    // expression is reported once by parse() itself — before any Set/JSON-pointer collapse in the
    // transformer. Asserting on the parse() list (not the Set-collecting helper) pins that.
    final var located =
        ClusterVariableReference.parse(
            expressionLanguage.parseExpression(
                "=camunda.vars.env.token + \"-\" + camunda.vars.env.token"));

    assertThat(located)
        .extracting(DetectedClusterVariable::clusterVariable)
        .containsExactly(new ClusterVariableReference("env", "token"));
  }

  @Test
  void shouldTreatWholeVariableAndFieldAccessAsDistinctReferences() {
    // given - one reference to the whole variable, one to a field of the same variable
    final var references = referencesIn("=camunda.vars.env.x + \"-\" + camunda.vars.env.x.f");

    // then
    assertThat(references)
        .containsExactlyInAnyOrder(
            new ClusterVariableReference("env", "x"),
            new ClusterVariableReference("env", "x", List.of("f")));
  }

  @Test
  void shouldTreatDifferentFieldAccessesAsDistinctReferences() {
    // given - two different fields of the same variable
    final var references =
        referencesIn("=camunda.vars.env.x.field1 + \"-\" + camunda.vars.env.x.field2");

    // then
    assertThat(references)
        .containsExactlyInAnyOrder(
            new ClusterVariableReference("env", "x", List.of("field1")),
            new ClusterVariableReference("env", "x", List.of("field2")));
  }

  @Test
  void shouldDropFieldPathWhenAccessBreaksQualifiedNameChain() {
    // indexed or parenthesized access is a separate AST node that breaks feel-scala's flat
    // qualified name, so the reference degrades to the whole variable and the trailing field path
    // is dropped. The job-join sub-issue keys on fieldPath, so this boundary is pinned here.
    assertThat(referencesIn("=camunda.vars.env.x[1].y"))
        .containsExactly(new ClusterVariableReference("env", "x"));
    assertThat(referencesIn("=(camunda.vars.env.x).y"))
        .containsExactly(new ClusterVariableReference("env", "x"));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(
      strings = {
        "=for camunda in [1, 2] return camunda.vars.env.token",
        "=some camunda in [1, 2] satisfies camunda.vars.env.token = 1",
        "=every camunda in [1, 2] satisfies camunda.vars.env.token = 1"
      })
  void shouldStillReportReferenceWhenRootIsShadowedByIteration(final String source) {
    // documented limitation: feel-scala suppresses only whole-name matches, so a bound 'camunda'
    // does not shadow a qualified camunda.vars.<scope>.<name> path; the reference is still reported
    assertThat(referencesIn(source)).containsExactly(new ClusterVariableReference("env", "token"));
  }

  @Test
  void shouldParseReferenceOutsideButNotInsideStringLiteralWithEscapedQuote() {
    final var source = "=\"a\\\"b camunda.vars.env.ignored\" + camunda.vars.env.real";
    assertThat(referencesIn(source)).containsExactly(new ClusterVariableReference("env", "real"));
  }

  @Test
  void shouldParseReferenceAfterStringLiteralEndingInEscapedBackslash() {
    final var source = "=\"a\\\\\" + camunda.vars.env.real";
    assertThat(referencesIn(source)).containsExactly(new ClusterVariableReference("env", "real"));
  }

  @Test
  void shouldReportContextPathForNestedReferences() {
    // given - a FEEL context with references at different depths and a literal to ignore
    final var source =
        "={a: camunda.vars.env.x, b: \"camunda.vars.env.y\", c: {d: camunda.vars.tenant.z}}";

    // when
    final var located = ClusterVariableReference.parse(expressionLanguage.parseExpression(source));

    // then - the literal 'y' is ignored; each reference carries the keys of its enclosing context
    assertThat(located)
        .extracting(DetectedClusterVariable::path, DetectedClusterVariable::clusterVariable)
        .containsExactlyInAnyOrder(
            tuple(List.of("a"), new ClusterVariableReference("env", "x")),
            tuple(List.of("c", "d"), new ClusterVariableReference("tenant", "z")));
  }

  static Stream<Arguments> sourcesWithReferences() {
    return Stream.of(
        arguments("=camunda.vars.env.myVar", Set.of(new ClusterVariableReference("env", "myVar"))),
        arguments(
            "=camunda.vars.tenant.myVar", Set.of(new ClusterVariableReference("tenant", "myVar"))),
        arguments(
            "=camunda.vars.cluster.myVar",
            Set.of(new ClusterVariableReference("cluster", "myVar"))),
        arguments(
            "=camunda.vars.env.myVar.a",
            Set.of(new ClusterVariableReference("env", "myVar", List.of("a")))),
        arguments(
            "=camunda.vars.env.myVar.a.b.c",
            Set.of(new ClusterVariableReference("env", "myVar", List.of("a", "b", "c")))),
        // whitespace and line breaks around the dots are insignificant in FEEL
        arguments(
            "=camunda . vars . env . myVar", Set.of(new ClusterVariableReference("env", "myVar"))),
        arguments(
            "=camunda\n  .vars\n  .env\n  .myVar",
            Set.of(new ClusterVariableReference("env", "myVar"))),
        // unicode names are valid FEEL identifiers
        arguments("=camunda.vars.env.myVár", Set.of(new ClusterVariableReference("env", "myVár"))),
        // backtick-escaped names allow special characters
        arguments(
            "=camunda.vars.env.`my-var`", Set.of(new ClusterVariableReference("env", "my-var"))),
        // a reference used inside a comment is not part of the expression
        arguments(
            "=camunda.vars.env.myVar // camunda.vars.env.other",
            Set.of(new ClusterVariableReference("env", "myVar"))),
        // reference nested inside a FEEL context value
        arguments(
            "={ v: camunda.vars.env.myVar }", Set.of(new ClusterVariableReference("env", "myVar"))),
        // a literal reference is ignored, a real reference after it is captured
        arguments(
            "=\"camunda.vars.env.a\" + camunda.vars.env.b",
            Set.of(new ClusterVariableReference("env", "b"))));
  }

  private Set<ClusterVariableReference> referencesIn(final String source) {
    return ClusterVariableReference.parse(expressionLanguage.parseExpression(source)).stream()
        .map(DetectedClusterVariable::clusterVariable)
        .collect(Collectors.toSet());
  }
}

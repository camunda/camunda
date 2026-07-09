/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.dmn.impl.VariablesContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class LongDecisionChainEvaluationTest {

  private static final int DECISION_COUNT = 10_000;
  // Small enough that a ~10,000-deep recursion reliably overflows it,
  // regardless of the JVM/CI runner's default thread stack size.
  private static final long SMALL_STACK_SIZE = 512 * 1024L;
  // Large enough to get past dmn-scala's own (not-yet-fixed on this pinned
  // version) parser recursion purely for test setup — see the eval-path test.
  private static final long LARGE_STACK_SIZE = 64L * 1024 * 1024;

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

  @Test
  @DisplayName(
      "Should turn a StackOverflowError from parsing a long decision chain into a graceful "
          + "failure instead of crashing")
  void shouldGracefullyFailInsteadOfCrashingOnParseStackOverflow() throws Throwable {
    // given
    final var drgXml = buildLongDecisionChainDrg(DECISION_COUNT);

    // when
    final var parsedDrg =
        runWithStackSize(
            SMALL_STACK_SIZE,
            () ->
                decisionEngine.parse(
                    new ByteArrayInputStream(drgXml.getBytes(StandardCharsets.UTF_8))));

    // then
    assertThat(parsedDrg.isValid())
        .describedAs("Expect the DRG to be marked invalid rather than crash while parsing")
        .isFalse();

    assertThat(parsedDrg.getFailureMessage())
        .describedAs("Expect the parse failure to be caused by a StackOverflowError")
        .contains("StackOverflowError");
  }

  @Test
  @DisplayName(
      "Should turn a StackOverflowError from evaluating a long decision chain into a graceful "
          + "failure instead of crashing")
  void shouldGracefullyFailInsteadOfCrashingOnEvaluationStackOverflow() throws Throwable {
    // given
    final var drgXml = buildLongDecisionChainDrg(DECISION_COUNT);
    // Parsed on a large stack purely so this test's setup can get past
    // dmn-scala's own unfixed parser recursion (camunda/dmn-scala#335) on
    // the version currently pinned in parent/pom.xml, isolating this test
    // to the eval()-path catch under test.
    final var parsedDrg =
        runWithStackSize(
            LARGE_STACK_SIZE,
            () ->
                decisionEngine.parse(
                    new ByteArrayInputStream(drgXml.getBytes(StandardCharsets.UTF_8))));

    assertThat(parsedDrg.isValid())
        .describedAs(
            "Setup parse must succeed on the large stack; if this fails, increase "
                + "LARGE_STACK_SIZE rather than treating it as the eval-path failure under test")
        .isTrue();

    // when
    final var result =
        runWithStackSize(
            SMALL_STACK_SIZE,
            () ->
                decisionEngine.evaluateDecisionById(
                    parsedDrg, "d0", new VariablesContext(Map.of())));

    // then
    assertThat(result.isFailure())
        .describedAs(
            "Expect the evaluation to fail gracefully rather than throw StackOverflowError")
        .isTrue();

    assertThat(result.getFailureMessage())
        .describedAs("Expect the failure message to explain the evaluation ran out of stack")
        .contains("StackOverflowError");
  }

  // Runs `body` on a dedicated thread with an explicit stack size, so that
  // whether a StackOverflowError is thrown does not depend on the JVM's or
  // CI runner's default thread stack size.
  private static <T> T runWithStackSize(final long stackSizeBytes, final Callable<T> body)
      throws Throwable {
    final var result = new AtomicReference<T>();
    final var error = new AtomicReference<Throwable>();

    final var thread =
        new Thread(
            null,
            () -> {
              try {
                result.set(body.call());
              } catch (final Throwable t) {
                error.set(t);
              }
            },
            "stack-size-test-thread",
            stackSizeBytes);

    thread.start();
    thread.join(Duration.ofSeconds(30).toMillis());

    if (thread.isAlive()) {
      throw new AssertionError(
          "stack-size-test-thread did not terminate within 30 seconds; assuming it hung "
              + "rather than waiting indefinitely");
    }

    if (error.get() != null) {
      throw error.get();
    }
    return result.get();
  }

  // Builds a linear DRG: d0 -> d1 -> d2 -> ... -> d(n-1), where d(n-1) = 1
  // and each d(i) = d(i+1) + 1. Mirrors the shape from camunda/dmn-scala#335.
  private static String buildLongDecisionChainDrg(final int decisionCount) {
    final var decisions = new StringBuilder();
    for (int i = 0; i < decisionCount; i++) {
      final boolean isLeaf = i == decisionCount - 1;
      decisions
          .append("<decision id=\"d")
          .append(i)
          .append("\" name=\"d")
          .append(i)
          .append("\">")
          .append("<variable id=\"v")
          .append(i)
          .append("\" name=\"v")
          .append(i)
          .append("\" />");
      if (!isLeaf) {
        final int next = i + 1;
        decisions
            .append("<informationRequirement id=\"ir")
            .append(i)
            .append("\">")
            .append("<requiredDecision href=\"#d")
            .append(next)
            .append("\" />")
            .append("</informationRequirement>")
            .append("<literalExpression id=\"le")
            .append(i)
            .append("\"><text>v")
            .append(next)
            .append(" + 1</text></literalExpression>");
      } else {
        decisions
            .append("<literalExpression id=\"le")
            .append(i)
            .append("\"><text>1</text></literalExpression>");
      }
      decisions.append("</decision>");
    }

    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\" "
        + "id=\"Definitions_chain\" name=\"Chain\" namespace=\"http://camunda.org/schema/1.0/dmn\">"
        + decisions
        + "</definitions>";
  }
}

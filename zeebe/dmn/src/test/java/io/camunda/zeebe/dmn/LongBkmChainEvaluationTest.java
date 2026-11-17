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

/**
 * These tests reproduce a real, uncaught {@link StackOverflowError} from the {@code dmn-scala}
 * library to verify {@code DmnScalaDecisionEngine}'s graceful-degradation catch blocks
 * (camunda/camunda#43232). They use a long chain of dependent <b>business knowledge models</b>
 * (BKMs), not decisions: the decision-chain recursion originally reported in camunda/dmn-scala#335
 * is fixed as of {@code dmn-scala} 1.12.2 (pinned in this repo), covering both the evaluator
 * ({@code DecisionEvaluator}) and the parser's cycle detection ({@code
 * DmnParser.hasDependencyCycle}) — so a long decision chain no longer overflows and can't be used
 * to reproduce this anymore. BKM-to-BKM dependency chains have a separate, still-unfixed recursion
 * in the same library ({@code BusinessKnowledgeEvaluator.createFunction} / {@code
 * evalRequiredKnowledge} at evaluation time, {@code DmnParser.parseBusinessKnowledgeModel} at parse
 * time), which reliably reproduces a genuine uncaught {@code StackOverflowError} today.
 *
 * <p>An artificially tiny thread stack size (independent of any real recursion) was considered and
 * rejected: on this JVM/platform, {@link Thread}'s {@code stackSize} hint gets silently clamped to
 * a usable floor well below 4 KiB, so it can't be shrunk far enough to overflow a shallow,
 * non-recursive workload — a real, sufficiently deep recursion is still required.
 */
final class LongBkmChainEvaluationTest {

  private static final int BKM_COUNT = 2_000;
  // Small enough that a ~2,000-deep recursion reliably overflows it,
  // regardless of the JVM/CI runner's default thread stack size.
  private static final long SMALL_STACK_SIZE = 512 * 1024L;
  // Large enough that parsing the same chain succeeds, for test setup.
  private static final long LARGE_STACK_SIZE = 64L * 1024 * 1024;

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

  @Test
  @DisplayName(
      "Should turn a StackOverflowError from parsing a long BKM chain into a graceful failure "
          + "instead of crashing")
  void shouldGracefullyFailInsteadOfCrashingOnParseStackOverflow() throws Throwable {
    // given
    final var drgXml = buildLongBkmChainDrg(BKM_COUNT);

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
      "Should turn a StackOverflowError from evaluating a long BKM chain into a graceful "
          + "failure instead of crashing")
  void shouldGracefullyFailInsteadOfCrashingOnEvaluationStackOverflow() throws Throwable {
    // given
    final var drgXml = buildLongBkmChainDrg(BKM_COUNT);
    // Parsed on a large stack purely so this test's setup succeeds, isolating this test to the
    // eval()-path catch under test.
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
    thread.setDaemon(true);

    thread.start();
    thread.join(Duration.ofSeconds(30).toMillis());

    if (thread.isAlive()) {
      thread.interrupt();
      throw new AssertionError(
          "stack-size-test-thread did not terminate within 30 seconds; assuming it hung "
              + "rather than waiting indefinitely");
    }

    if (error.get() != null) {
      throw error.get();
    }
    return result.get();
  }

  // Builds a decision d0 that requires a long chain of business knowledge models,
  // bkm0 -> bkm1 -> ... -> bkm(n-1), via knowledgeRequirement. Evaluating/parsing d0 pulls in
  // the whole BKM chain regardless of whether d0's own logic invokes it.
  private static String buildLongBkmChainDrg(final int bkmCount) {
    final var bkms = new StringBuilder();
    for (int i = 0; i < bkmCount; i++) {
      final boolean isLeaf = i == bkmCount - 1;
      bkms.append("<businessKnowledgeModel id=\"bkm")
          .append(i)
          .append("\" name=\"bkm")
          .append(i)
          .append("\">")
          .append("<encapsulatedLogic><literalExpression><text>1</text></literalExpression>")
          .append("</encapsulatedLogic>");
      if (!isLeaf) {
        final int next = i + 1;
        bkms.append("<knowledgeRequirement><requiredKnowledge href=\"#bkm")
            .append(next)
            .append("\" /></knowledgeRequirement>");
      }
      bkms.append("</businessKnowledgeModel>");
    }

    final var decision =
        "<decision id=\"d0\" name=\"d0\">"
            + "<variable id=\"v0\" name=\"v0\" />"
            + "<knowledgeRequirement><requiredKnowledge href=\"#bkm0\" /></knowledgeRequirement>"
            + "<literalExpression><text>1</text></literalExpression>"
            + "</decision>";

    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\" "
        + "id=\"Definitions_bkmchain\" name=\"BkmChain\" namespace=\"http://camunda.org/schema/1.0/dmn\">"
        + decision
        + bkms
        + "</definitions>";
  }
}

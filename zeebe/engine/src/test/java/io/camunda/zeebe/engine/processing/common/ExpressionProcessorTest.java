/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.EvaluationContextLookup;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.EvaluationException;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpressionProcessorTest {

  private static final ExpressionLanguage EXPRESSION_LANGUAGE =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private static final EvaluationContext EMPTY_LOOKUP = x -> null;
  private static final EvaluationContextLookup DEFAULT_CONTEXT_LOOKUP = scope -> EMPTY_LOOKUP;
  private static final Duration DEFAULT_TIMEOUT =
      EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT;

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class EvaluateArrayOfStringsExpressionTest {

    @ParameterizedTest
    @MethodSource("arrayOfStringsExpressions")
    void testSuccessfulEvaluations(final String expression, final List<String> expected) {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression(expression);
      assertThat(processor.evaluateArrayOfStringsExpression(parsedExpression, -1L))
          .isRight()
          .extracting(Either::get)
          .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("notArrayOfStringsExpressions")
    void testFailingEvaluations(final String expression, final String message) {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression(expression);
      assertThat(processor.evaluateArrayOfStringsExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(Either::getLeft)
          .extracting(Failure::getMessage)
          .isEqualTo(message);
    }

    Stream<Arguments> arrayOfStringsExpressions() {
      return Stream.of(
          Arguments.of("= []", List.of()),
          Arguments.of("= [\"a\"]", List.of("a")),
          Arguments.of("= [\"a\",\"b\"]", List.of("a", "b")));
    }

    Stream<Arguments> notArrayOfStringsExpressions() {
      return Stream.of(
          Arguments.of(
              "= \"a\"",
              "Expected result of the expression ' \"a\"' to be 'ARRAY', but was 'STRING'."),
          Arguments.of(
              "= 1", "Expected result of the expression ' 1' to be 'ARRAY', but was 'NUMBER'."),
          Arguments.of(
              "= {}", "Expected result of the expression ' {}' to be 'ARRAY', but was 'OBJECT'."),
          Arguments.of(
              "[]", "Expected result of the expression '[]' to be 'ARRAY', but was 'STRING'."),
          Arguments.of(
              "= [1,2,3]",
              "Expected result of the expression ' [1,2,3]' to be 'ARRAY' containing 'STRING' items, but was 'ARRAY' containing at least one non-'STRING' item."),
          Arguments.of(
              "= [{},{}]",
              "Expected result of the expression ' [{},{}]' to be 'ARRAY' containing 'STRING' items, but was 'ARRAY' containing at least one non-'STRING' item."),
          Arguments.of(
              "= [null]",
              "Expected result of the expression ' [null]' to be 'ARRAY' containing 'STRING' items, but was 'ARRAY' containing at least one non-'STRING' item."));
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class EvaluationWarningsTest {

    @Test
    void testStringExpression() {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateStringExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be 'STRING', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testLongExpression() {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateLongExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be 'NUMBER', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testBooleanExpression() {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateBooleanExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be 'BOOLEAN', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testIntervalExpression() {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateIntervalExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be one of '[DURATION, PERIOD, STRING]', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testDateTimeExpression() {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateDateTimeExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be one of '[DATE_TIME, STRING]', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testArrayExpression() {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateArrayExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be 'ARRAY', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testStringArrayExpression() {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=[x]");
      assertThat(processor.evaluateArrayOfStringsExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression '[x]' to be 'ARRAY' containing 'STRING' items, \
              but was 'ARRAY' containing at least one non-'STRING' item. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testMessageCorrelationKeyExpression() {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateMessageCorrelationKeyExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Failed to extract the correlation key for 'x': \
              The value must be either a string or a number, but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }

    @Test
    void testVariableMappingExpression() {
      final var processor =
          new ExpressionProcessor(EXPRESSION_LANGUAGE, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var parsedExpression = EXPRESSION_LANGUAGE.parseExpression("=x");
      assertThat(processor.evaluateVariableMappingExpression(parsedExpression, -1L))
          .isLeft()
          .extracting(r -> r.getLeft().getMessage())
          .isEqualTo(
              """
              Expected result of the expression 'x' to be 'OBJECT', but was 'NULL'. \
              The evaluation reported the following warnings:
              [NO_VARIABLE_FOUND] No variable found with name 'x'""");
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class EvaluationErrorsTest {

    /**
     * This test case uses an expression language implementation that blocks until released. It
     * verifies that the processor returns a Failure (left) when the evaluation times out.
     */
    @ParameterizedTest(name = "{1} returns Failure on timeout")
    @MethodSource("processorEvaluationMethods")
    void shouldReturnFailureWhenEvaluationTimesOut(
        final ProcessorMethod evaluationMethod, final String methodName) {
      // given
      final var blockingEL = new FakeBlockingExpressionLanguage();
      final var processor =
          new ExpressionProcessor(blockingEL, DEFAULT_CONTEXT_LOOKUP, Duration.ofMillis(1));
      final var expression = EXPRESSION_LANGUAGE.parseExpression("foo");

      try {
        // when
        final var result = evaluationMethod.evaluate(processor, expression);

        // then
        assertThat(result)
            .as("should return failure for %s", methodName)
            .isLeft()
            .extracting(Either::getLeft)
            .extracting(Failure::getMessage)
            .isEqualTo("Expected to evaluate expression but timed out after 1 ms: 'foo'");
      } finally {
        // release the blocking EL to avoid leaking threads
        blockingEL.release();
      }
    }

    /**
     * This test case uses an expression language implementation that blocks until released. It
     * verifies that the processor interrupts the evaluation thread when the evaluation times out.
     */
    @ParameterizedTest(name = "{1} interrupts thread on timeout")
    @MethodSource("processorEvaluationMethods")
    void shouldInterruptBlockedThreadOnTimeout(
        final ProcessorMethod evaluationMethod, final String methodName)
        throws InterruptedException {
      // given
      final var interruptedLatch = new CountDownLatch(1);

      final var blockingEL =
          new FakeBlockingExpressionLanguage(onInterrupted -> interruptedLatch.countDown());

      final var processor =
          new ExpressionProcessor(blockingEL, DEFAULT_CONTEXT_LOOKUP, Duration.ofMillis(10));
      final var expression = EXPRESSION_LANGUAGE.parseExpression("foo");

      // when
      final var result = evaluationMethod.evaluate(processor, expression);

      // then
      assertThat(result).as("should return failure for %s", methodName).isLeft();
      Assertions.assertThat(interruptedLatch.await(5, TimeUnit.SECONDS))
          .describedAs("Expected the evaluation thread to be interrupted upon timeout")
          .isTrue();
    }

    /**
     * This test case uses an expression language implementation that always throws a specific
     * exception when evaluating an expression. It verifies that the processor catches this
     * exception and rethrows it as an EvaluationException with the expected message and cause.
     */
    @ParameterizedTest(name = "{1} throws EvaluationException on error")
    @MethodSource("processorEvaluationMethods")
    void shouldThrowEvaluationExceptionWhenExceptionThrown(final ProcessorMethod evaluationMethod) {
      // given
      final var processor =
          new ExpressionProcessor(
              new FakeThrowingExpressionLanguage(new CustomException("custom failure")),
              DEFAULT_CONTEXT_LOOKUP,
              DEFAULT_TIMEOUT);
      final var expression = EXPRESSION_LANGUAGE.parseExpression("foo");

      // when & then
      Assertions.assertThatThrownBy(() -> evaluationMethod.evaluate(processor, expression))
          .isInstanceOf(EvaluationException.class)
          .hasMessage("Expected to evaluate expression 'foo', but an exception was thrown")
          .hasCauseInstanceOf(CustomException.class)
          .hasRootCauseMessage("custom failure");
    }

    /**
     * This test case uses an expression language implementation that blocks indefinitely when
     * evaluating an expression. The test starts the evaluation in a separate thread, waits until
     * the evaluation has started, then interrupts the thread and verifies that the processor
     * responds to the interruption by throwing an EvaluationException.
     */
    @ParameterizedTest(name = "{1} throws EvaluationException on interruption")
    @MethodSource("processorEvaluationMethods")
    void shouldThrowEvaluationExceptionWhenInterrupted(
        final ProcessorMethod evaluationMethod, final String methodName) throws Exception {
      // given
      final var blockingEL = new FakeBlockingExpressionLanguage();
      final var processor =
          new ExpressionProcessor(blockingEL, DEFAULT_CONTEXT_LOOKUP, DEFAULT_TIMEOUT);
      final var expression = EXPRESSION_LANGUAGE.parseExpression("foo");

      final var failure = new AtomicReference<Throwable>();
      final var evaluationThread =
          new Thread(
              () -> {
                try {
                  evaluationMethod.evaluate(processor, expression);
                } catch (final Throwable t) {
                  failure.set(t);
                }
              },
              "expression-processor-interrupt-test");

      // when
      evaluationThread.start();

      Assertions.assertThat(blockingEL.awaitEntered(5, TimeUnit.SECONDS))
          .as("evaluation should start and block for %s", methodName)
          .isTrue();

      evaluationThread.interrupt();
      blockingEL.release(); // unblock EL so it can notice interrupt/fail fast
      evaluationThread.join(TimeUnit.SECONDS.toMillis(5));

      // then
      Assertions.assertThat(evaluationThread.isAlive())
          .as("evaluation thread should terminate after interruption for %s", methodName)
          .isFalse();

      Assertions.assertThat(failure.get())
          .isInstanceOf(EvaluationException.class)
          .hasMessage("Expected to evaluate expression 'foo', but the evaluation was interrupted")
          .hasCauseInstanceOf(InterruptedException.class);
    }

    Stream<Arguments> processorEvaluationMethods() {
      return Stream.of(
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) -> processor.evaluateStringExpression(expression, -1L),
              "evaluateStringExpression"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) ->
                      processor.evaluateStringExpressionAsDirectBuffer(expression, -1L),
              "evaluateStringExpressionAsDirectBuffer"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) -> processor.evaluateLongExpression(expression, -1L),
              "evaluateLongExpression"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) -> processor.evaluateBooleanExpression(expression, -1L),
              "evaluateBooleanExpression"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) -> processor.evaluateIntervalExpression(expression, -1L),
              "evaluateIntervalExpression"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) -> processor.evaluateDateTimeExpression(expression, -1L),
              "evaluateDateTimeExpression"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) ->
                      processor.evaluateDateTimeExpression(expression, -1L, false),
              "evaluateDateTimeExpression(nullableFlag)"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) -> processor.evaluateAnyExpression(expression, -1L),
              "evaluateAnyExpression"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) -> processor.evaluateArrayExpression(expression, -1L),
              "evaluateArrayExpression"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) ->
                      processor.evaluateArrayOfStringsExpression(expression, -1L),
              "evaluateArrayOfStringsExpression"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) ->
                      processor.evaluateMessageCorrelationKeyExpression(expression, -1L),
              "evaluateMessageCorrelationKeyExpression"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) -> processor.evaluateIntegerExpression(expression, -1L),
              "evaluateIntegerExpression"),
          Arguments.of(
              (ProcessorMethod)
                  (processor, expression) ->
                      processor.evaluateVariableMappingExpression(expression, -1L),
              "evaluateVariableMappingExpression"));
    }

    private static final class FakeBlockingExpressionLanguage implements ExpressionLanguage {
      private final CountDownLatch entered = new CountDownLatch(1);
      private final CountDownLatch release = new CountDownLatch(1);
      private final Consumer<InterruptedException> onInterrupted;

      public FakeBlockingExpressionLanguage() {
        this(ignored -> {});
      }

      public FakeBlockingExpressionLanguage(final Consumer<InterruptedException> onInterrupted) {
        this.onInterrupted = onInterrupted;
      }

      @SuppressWarnings("SameParameterValue")
      boolean awaitEntered(final long timeout, final TimeUnit unit) throws InterruptedException {
        return entered.await(timeout, unit);
      }

      void release() {
        release.countDown();
      }

      @Override
      public Expression parseExpression(final String expression) {
        // not used in these tests
        return null;
      }

      @Override
      public EvaluationResult evaluateExpression(
          final Expression expression, final EvaluationContext context) {
        entered.countDown();
        try {
          // deterministically block until the test releases; if interrupted while waiting,
          // propagate
          release.await();
          return null; // not relevant for this test
        } catch (final InterruptedException e) {
          onInterrupted.accept(e);
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
    }

    private static class CustomException extends RuntimeException {
      public CustomException(final String message) {
        super(message);
      }
    }

    private record FakeThrowingExpressionLanguage(RuntimeException throwsException)
        implements ExpressionLanguage {

      @Override
      public Expression parseExpression(final String expression) {
        // not used in these tests
        return null;
      }

      @Override
      public EvaluationResult evaluateExpression(
          final Expression expression, final EvaluationContext context) {
        throw throwsException;
      }
    }

    @FunctionalInterface
    private interface ProcessorMethod {
      Either<Failure, ?> evaluate(ExpressionProcessor processor, Expression expression);
    }
  }
}

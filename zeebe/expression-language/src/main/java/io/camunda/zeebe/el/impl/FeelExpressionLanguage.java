/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import static io.camunda.zeebe.util.EnsureUtil.ensureNotNull;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.EvaluationWarning;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.feel.impl.FeelFunctionProvider;
import io.camunda.zeebe.feel.impl.FeelToMessagePackTransformer;
import io.camunda.zeebe.feel.impl.MessagePackValueMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.FeelEngine.Failure;
import org.camunda.feel.FeelEngineClock;
import org.camunda.feel.impl.JavaValueMapper;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.valuemapper.CustomValueMapper;
import scala.Function1;
import scala.Option;
import scala.util.Either;

/**
 * A wrapper around the FEEL-Scala expression language.
 *
 * <p>
 * <li><a href="https://github.com/camunda/feel-scala">GitHub Repository</a>
 * <li><a href="https://camunda.github.io/feel-scala">Documentation</a>
 */
public final class FeelExpressionLanguage implements ExpressionLanguage {

  private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\=(.+)", Pattern.DOTALL);

  private static final boolean SHARE_ENGINE =
      Boolean.getBoolean("zeebe.experimental.keepEngineAcrossTransitions");

  /** The clock of the expression language evaluating on this thread, for the shared engine. */
  private static final ThreadLocal<FeelEngineClock> EVALUATION_CLOCK = new ThreadLocal<>();

  private final FeelToMessagePackTransformer messagePackTransformer =
      new FeelToMessagePackTransformer();

  private final FeelEngine feelEngine;
  private final FeelEngineClock clock;
  private final ExpressionLanguageMetrics metrics;

  public FeelExpressionLanguage(final FeelEngineClock clock) {
    this(clock, ExpressionLanguageMetrics.noop());
  }

  public FeelExpressionLanguage(final FeelEngineClock clock, final MeterRegistry meterRegistry) {
    this(clock, new ExpressionLanguageMetricsImpl(meterRegistry));
  }

  public FeelExpressionLanguage(
      final FeelEngineClock clock, final ExpressionLanguageMetrics metrics) {
    this.clock = clock;
    feelEngine = SHARE_ENGINE ? SharedFeelEngine.INSTANCE : buildEngine(clock);
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
  }

  private static FeelEngine buildEngine(final FeelEngineClock clock) {
    return buildEngine(clock, new MessagePackValueMapper());
  }

  private static FeelEngine buildEngine(
      final FeelEngineClock clock, final CustomValueMapper valueMapper) {
    return new FeelEngine.Builder()
        .customValueMapper(valueMapper)
        .functionProvider(new FeelFunctionProvider())
        .clock(clock)
        .build();
  }

  @Override
  public Expression parseExpression(final String expression) {
    ensureNotNull("expression", expression);

    final var expressionMatcher = EXPRESSION_PATTERN.matcher(expression);

    if (expressionMatcher.matches()) {
      final var unpackedExpression = expressionMatcher.group(1);
      return parseFeelExpression(unpackedExpression);
    } else {
      return new StaticExpression(expression);
    }
  }

  @Override
  public EvaluationResult evaluateExpression(
      final Expression expression, final EvaluationContext context) {
    ensureNotNull("expression", expression);
    ensureNotNull("context", context);

    if (!expression.isValid()) {
      final var failureMessage = expression.getFailureMessage();
      return new EvaluationFailure(expression, failureMessage);

    } else if (expression instanceof StaticExpression) {
      final var staticExpression = (StaticExpression) expression;
      return staticExpression;

    } else if (expression instanceof NullExpression) {
      final var nullExpression = (NullExpression) expression;
      return nullExpression;

    } else if (expression instanceof FeelExpression) {
      final var feelExpression = (FeelExpression) expression;
      return evaluateFeelExpression(expression, context, feelExpression);
    }

    throw new IllegalArgumentException(
        String.format("Expected FEEL expression or static value but found '%s'", expression));
  }

  private Expression parseFeelExpression(final String expression) {
    final long startNanos = System.nanoTime();
    try {
      final Either<Failure, ParsedExpression> parseResult = feelEngine.parseExpression(expression);
      final long durationNanos = System.nanoTime() - startNanos;

      if (parseResult.isLeft()) {
        final var failure = parseResult.left().get();
        metrics.recordParsingDurationFailure(durationNanos);
        return new InvalidExpression(expression, failure.message());
      } else {
        metrics.recordParsingDurationSuccess(durationNanos);
        final var parsedExpression = parseResult.right().get();
        return new FeelExpression(parsedExpression);
      }
    } catch (final Exception e) {
      final long durationNanos = System.nanoTime() - startNanos;
      metrics.recordParsingDurationFailure(durationNanos);
      throw e;
    }
  }

  private EvaluationResult evaluateFeelExpression(
      final Expression expression,
      final EvaluationContext context,
      final FeelExpression feelExpression) {

    final var parsedExpression = feelExpression.getParsedExpression();
    final var feelContext = new FeelVariableContext(context);

    final long startNanos = System.nanoTime();
    try {
      final var evaluationResult = evaluate(parsedExpression, feelContext);
      final long durationNanos = System.nanoTime() - startNanos;

      logSlowEvaluationIfNeeded(expression, durationNanos);

      final var evaluationWarnings = extractEvaluationWarning(evaluationResult);
      if (evaluationResult.isFailure()) {
        metrics.recordEvaluationDurationFailure(durationNanos);
        final var failureMessage = evaluationResult.failure().message();
        return new EvaluationFailure(expression, failureMessage, evaluationWarnings);
      }

      metrics.recordEvaluationDurationSuccess(durationNanos);
      final var result = evaluationResult.result();
      if (result instanceof Val) {
        return new FeelEvaluationResult(
            expression, (Val) result, evaluationWarnings, messagePackTransformer::toMessagePack);
      } else {
        throw new IllegalStateException(
            String.format(
                "Expected FEEL evaluation result to be of type '%s' but was '%s'",
                Val.class, result.getClass()));
      }
    } catch (final Exception e) {
      final long durationNanos = System.nanoTime() - startNanos;
      metrics.recordEvaluationDurationFailure(durationNanos);
      throw e;
    }
  }

  private org.camunda.feel.api.EvaluationResult evaluate(
      final ParsedExpression parsedExpression, final FeelVariableContext feelContext) {
    if (!SHARE_ENGINE) {
      return feelEngine.evaluate(parsedExpression, feelContext);
    }
    final var previousClock = EVALUATION_CLOCK.get();
    EVALUATION_CLOCK.set(clock);
    try {
      return feelEngine.evaluate(parsedExpression, feelContext);
    } finally {
      EVALUATION_CLOCK.set(previousClock);
    }
  }

  private void logSlowEvaluationIfNeeded(final Expression expression, final long durationNanos) {
    if (metrics.isSlowEvaluation(durationNanos)) {
      Loggers.LOGGER.warn(
          "Slow FEEL expression evaluation detected: expression '{}' took {} ms (threshold: {} ms)",
          expression.getExpression(),
          TimeUnit.NANOSECONDS.toMillis(durationNanos),
          metrics.getSlowEvaluationThresholdMs());
    }
  }

  private List<EvaluationWarning> extractEvaluationWarning(
      final org.camunda.feel.api.EvaluationResult evaluationResult) {
    final var warnings = new ArrayList<EvaluationWarning>();
    evaluationResult
        .suppressedFailures()
        .foreach(
            suppressedFailure -> {
              final var warning =
                  new FeelEvaluationWarning(
                      suppressedFailure.failureType().toString(),
                      suppressedFailure.failureMessage());
              return warnings.add(warning);
            });
    return warnings;
  }

  /**
   * One FEEL engine per broker. Every engine rebuild on a role change creates several expression
   * languages, and building a FEEL engine is costly; the engine reads the evaluating expression
   * language's clock instead of holding one, and a value mapper per thread, since the mapper reads
   * variables through a reader of its own.
   */
  private static final class SharedFeelEngine {
    private static final ThreadLocal<MessagePackValueMapper> VALUE_MAPPER =
        ThreadLocal.withInitial(MessagePackValueMapper::new);

    private static final FeelEngine INSTANCE =
        buildEngine(
            new FeelEngineClock() {
              @Override
              public ZonedDateTime getCurrentTime() {
                final var clock = EVALUATION_CLOCK.get();
                return clock != null ? clock.getCurrentTime() : ZonedDateTime.now();
              }
            },
            new JavaValueMapper() {
              @Override
              public Option<Val> toVal(
                  final Object x, final Function1<Object, Val> innerValueMapper) {
                return VALUE_MAPPER.get().toVal(x, innerValueMapper);
              }

              @Override
              public Option<Object> unpackVal(
                  final Val value, final Function1<Val, Object> innerValueMapper) {
                return VALUE_MAPPER.get().unpackVal(value, innerValueMapper);
              }
            });
  }
}

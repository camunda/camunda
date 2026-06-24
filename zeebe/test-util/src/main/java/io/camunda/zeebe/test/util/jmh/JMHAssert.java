/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.jmh;

import java.text.DecimalFormat;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.error.BasicErrorMessageFactory;
import org.openjdk.jmh.results.RunResult;

/** Convenience class to assert results of a JMH test (via {@link RunResult}. */
public final class JMHAssert extends AbstractAssert<JMHAssert, RunResult> {

  private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.000");

  /**
   * @param actual the actual results
   */
  public JMHAssert(final RunResult actual) {
    super(actual, JMHAssert.class);
  }

  /**
   * A convenience factory method that's consistent with AssertJ conventions.
   *
   * @param actual the actual test results
   * @return an instance of {@link JMHAssert} to assert properties of the benchmark
   */
  public static JMHAssert assertThat(final RunResult actual) {
    return new JMHAssert(actual);
  }

  /**
   * Asserts that the result of this benchmark are within one deviation of the reference score.
   *
   * @param referenceScore the expected reference score
   * @param maxDeviation the maximum deviation, as a float from 0 to 1
   * @return itself for chaining
   */
  @SuppressWarnings("UnusedReturnValue")
  public JMHAssert isWithinDeviation(final double referenceScore, final double maxDeviation) {
    final double score = actual.getPrimaryResult().getScore();
    final double deviation = Math.abs(score / referenceScore - 1);

    if (deviation > maxDeviation) {
      throwAssertionError(
          new BasicErrorMessageFactory(
              "Expected reference score is %s got %s, deviation %s exceeds maximum allowed deviation %s",
              DECIMAL_FORMAT.format(referenceScore),
              DECIMAL_FORMAT.format(score),
              DECIMAL_FORMAT.format(deviation * 100) + "%",
              DECIMAL_FORMAT.format(maxDeviation * 100) + "%"));
    }

    return myself;
  }

  /**
   * Asserts that the result of this benchmark is at least the expected reference score.
   *
   * @param referenceScore the expected reference score
   * @param maxDeviation the maximum allowed deviation used to compute a real minimum score
   * @return itself for chaining
   */
  @SuppressWarnings("UnusedReturnValue")
  public JMHAssert isAtLeast(final double referenceScore, final double maxDeviation) {
    final double score = actual.getPrimaryResult().getScore();
    final double minimumScore = referenceScore - referenceScore * maxDeviation;

    if (score < minimumScore) {
      throwAssertionError(
          new BasicErrorMessageFactory(
              "Expected reference score to be at least %s (with %s max deviation, i.e. %s), but got %s",
              DECIMAL_FORMAT.format(referenceScore),
              DECIMAL_FORMAT.format(maxDeviation * 100) + "%",
              DECIMAL_FORMAT.format(minimumScore),
              DECIMAL_FORMAT.format(score)));
    }

    return myself;
  }
}

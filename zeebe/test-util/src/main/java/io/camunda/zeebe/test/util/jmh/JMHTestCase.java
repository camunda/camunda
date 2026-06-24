/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.jmh;

import java.util.Objects;
import java.util.function.Consumer;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * A small utility to help you build and run JMH benchmarks as JUnit tests. Use with {@link
 * JMHAssert} to assert the results.
 *
 * <p>You can reuse this in your JUnit tests:
 *
 * <pre>{@code
 * &#64;Test
 * void shouldBenchmark() {
 *   // given
 *   final var testCase = JMHTestCase.of(MyBenchmarkClass.class, "measurePropertyFoo");
 *
 *   // when
 *   final var assertResult = testCase.run();
 *
 *   // then
 *   assertResult.isWithinDeviation(3.01, 0.2);
 * }
 * }</pre>
 */
public final class JMHTestCase {
  private final ChainedOptionsBuilder builder;

  private JMHTestCase(final ChainedOptionsBuilder builder) {
    this.builder = Objects.requireNonNull(builder, "must specify a JMH builder");
  }

  /**
   * Builds a new {@link JMHTestCase} which will run the given benchmark method ({@code testMethod})
   * in the given test class.
   *
   * <p>NOTE: leaving the method blank will run all benchmarks in the test class. Keep in mind
   * however that the {@link #run()} method expects to run a single benchmark only, so if you have
   * multiple in the same class and don't specify them, then the behavior is undefined.
   *
   * @param testClass the benchmark class to run
   * @param testMethod the actual benchmark to run in this class
   * @param modifier an optional modifier, useful to pass params
   * @return a new configured test case
   */
  public static JMHTestCase of(
      final Class<?> testClass,
      final String testMethod,
      final Consumer<ChainedOptionsBuilder> modifier) {
    final var testMatcher = new StringBuilder();
    testMatcher.append("^\\Q").append(testClass.getName());
    if (testMethod == null || testMethod.isBlank()) {
      testMatcher.append("\\E");
    } else {
      testMatcher.append(".").append(testMethod).append("\\E$");
    }

    final var builder = new OptionsBuilder().include(testMatcher.toString());
    if (modifier != null) {
      modifier.accept(builder);
    }

    return new JMHTestCase(builder);
  }

  /**
   * Convenience method when no further modification is required than specifying the benchmark.
   *
   * @see #of(Class, String, Consumer)
   */
  public static JMHTestCase of(final Class<?> testClass, final String testMethod) {
    return of(testClass, testMethod, ignored -> {});
  }

  /**
   * Allows setting custom options to the underlying JMH options builder.
   *
   * @param modifier a consumer which modifies the options builder
   * @return itself for chaining
   */
  public JMHTestCase withOptions(final Consumer<ChainedOptionsBuilder> modifier) {
    modifier.accept(builder);
    return this;
  }

  /**
   * Runs the configured benchmark, returning the assertions immediately for the result.
   *
   * @return assertions to verify the benchmark results
   */
  public JMHAssert run() {
    final var runner = new Runner(builder.build());

    try {
      return JMHAssert.assertThat(runner.runSingle());
    } catch (final RunnerException e) {
      throw new RuntimeException(e);
    }
  }
}

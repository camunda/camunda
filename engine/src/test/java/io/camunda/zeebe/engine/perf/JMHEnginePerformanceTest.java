/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.perf;

import java.text.DecimalFormat;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@EnabledIfEnvironmentVariable(named = "ENGINE_PERFORMANCE_TESTS_ENABLED", matches = "true")
public class JMHEnginePerformanceTest {

  private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.000");
  private static final double REFERENCE_SCORE = 450; // just for CI, locally we reached ~680
  private static final double MAX_DEVIATION = 0.15; // 15% of deviation is allowed

  @Test
  public void runJmhBenchmark() throws RunnerException {
    // given
    final var opt =
        new OptionsBuilder().include(EngineLargeStatePerformanceTest.class.getSimpleName()).build();

    // when
    final var runResults = new Runner(opt).run();

    // then
    for (final RunResult runResult : runResults) {
      // we are fine with around 10 percent deviation but not more
      assertDeviationWithin(runResult, REFERENCE_SCORE, MAX_DEVIATION);
    }
  }

  private static void assertDeviationWithin(
      final RunResult result, final double referenceScore, final double maxDeviation) {
    final double score = result.getPrimaryResult().getScore();
    final double deviation = Math.abs(score / referenceScore - 1);

    Assertions.assertThat(deviation)
        .withFailMessage(
            () -> {
              final String deviationString = DECIMAL_FORMAT.format(deviation * 100) + "%";
              final String maxDeviationString = DECIMAL_FORMAT.format(maxDeviation * 100) + "%";

              return String.format(
                  "Expected reference score is '%.2f' got '%.2f', deviation %s exceeds maximum allowed deviation %s",
                  REFERENCE_SCORE, score, deviationString, maxDeviationString);
            })
        .isLessThan(maxDeviation);
  }
}

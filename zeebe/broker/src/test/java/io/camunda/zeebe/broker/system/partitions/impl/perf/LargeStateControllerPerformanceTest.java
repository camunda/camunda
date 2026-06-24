/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.perf;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.logstreams.state.DbPositionSupplier;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Warmup(iterations = 50, time = 1)
@Measurement(iterations = 25, time = 1)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class LargeStateControllerPerformanceTest {
  private static final Logger LOG =
      LoggerFactory.getLogger(LargeStateControllerPerformanceTest.class);
  private static final double ONE_GB = Math.pow(2.0, 30.0);
  private static final double SIZE_GB =
      Double.parseDouble(
          System.getenv().getOrDefault("LARGE_STATE_CONTROLLER_PERFORMANCE_TEST_SIZE_GB", "0.5"));
  private static final Map<Double, Double> KNOWN_REFERENCE_SCORES = Map.of(0.5, 10.0, 4.0, 10.0);

  private TestState.TestContext context;

  @Setup
  public void setup() throws Throwable {
    final var sizeInBytes = Math.round(SIZE_GB * ONE_GB);

    LOG.info("Creating a test state of approximately {}GB; please hold the line...", SIZE_GB);
    context = new TestState().generateContext(sizeInBytes);
    LOG.info(
        "Created a test size with a total size of {}GB",
        String.format("%.3f", context.snapshotSize() / ONE_GB));
  }

  @TearDown
  public void tearDown() throws Exception {
    context.close();
  }

  @JMHTest("measureStateRecovery")
  void shouldRecoverStateWithinDeviation(final JMHTestCase testCase) {
    // given
    final var referenceScore = KNOWN_REFERENCE_SCORES.getOrDefault(SIZE_GB, 0.0);
    assertThat(KNOWN_REFERENCE_SCORES)
        .as("map of reference scores contains an entry for the desired size")
        .containsKey(SIZE_GB);

    // when
    final var assertResult = testCase.run();

    // then
    assertResult.isAtLeast(referenceScore, 0.2);
  }

  @Benchmark
  public Optional<String> measureStateRecovery() throws Exception {
    // given
    final var controller =
        new StateControllerImpl(
            context.dbFactory(),
            context.snapshotStore(),
            context.temporaryFolder().resolve("runtime"),
            ignored -> Optional.empty(),
            zeebeDb -> new DbPositionSupplier(zeebeDb, false),
            context.snapshotStore());

    // when
    try (controller) {
      // the controller closing will close the DB we just opened
      //noinspection resource
      final var db = controller.recover().join();

      //noinspection unchecked
      return db.getProperty("rocksdb.estimate-live-data-size");
    }
  }
}

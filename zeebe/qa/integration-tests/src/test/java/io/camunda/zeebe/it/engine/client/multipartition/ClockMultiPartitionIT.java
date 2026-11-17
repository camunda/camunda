/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.multipartition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers adding time to the clock of a broker with several partitions. Each add builds on the
 * current clock, so the gateway sends every clock change to the same partition, which passes them
 * on to the others in order. If adds were made on different partitions, adds sent at the same time
 * would each start from the same clock, and some of the added time would be lost.
 *
 * <p>An engine test writes the commands to a partition itself, so it doesn't show where the gateway
 * sends them.
 */
@ZeebeIntegration
final class ClockMultiPartitionIT {

  private static final int PARTITION_COUNT = 3;
  private static final int ADD_COUNT = 2 * PARTITION_COUNT;
  private static final Duration ADDED_DURATION = Duration.ofHours(1);
  private static final Duration TOTAL_ADDED_DURATION = ADDED_DURATION.multipliedBy(ADD_COUNT);

  @TestZeebe(initMethod = "initTestStandaloneBroker", partitionCount = PARTITION_COUNT)
  private static TestStandaloneBroker broker;

  @AutoClose private final CamundaClient client = broker.newClientBuilder().build();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    // the annotation's partition count is what the topology is awaited against, the cluster
    // configuration is what the broker is started with, so both are needed
    broker =
        new TestStandaloneBroker()
            .withUnauthenticatedAccess()
            .withClusterConfig(cluster -> cluster.setPartitionCount(PARTITION_COUNT));
  }

  @BeforeEach
  void resetClock() {
    client.newResetClockCommand().send().join();
  }

  @Test
  void shouldKeepEveryDurationAddedOneAfterAnother() {
    // when
    for (int i = 0; i < ADD_COUNT; i++) {
      client.newAddClockCommand().offset(ADDED_DURATION).send().join();
    }

    // then
    awaitClockOfEveryPartitionAheadBy(TOTAL_ADDED_DURATION);
  }

  @Test
  void shouldKeepEveryDurationAddedAtTheSameTime() {
    // when
    IntStream.range(0, ADD_COUNT)
        .mapToObj(i -> client.newAddClockCommand().offset(ADDED_DURATION).send())
        .toList()
        .forEach(CamundaFuture::join);

    // then
    awaitClockOfEveryPartitionAheadBy(TOTAL_ADDED_DURATION);
  }

  private void awaitClockOfEveryPartitionAheadBy(final Duration expected) {
    Awaitility.await(
            "until the clock of every partition is ahead of the system time by " + expected)
        .untilAsserted(
            () ->
                assertThat(readHowFarEveryPartitionIsAhead())
                    .describedAs("how far the clock of each partition is ahead of the system time")
                    .containsOnly(expected));
  }

  /**
   * Reads the clock once from each partition, because the gateway sends consecutive clock reads to
   * the partitions in turn. The result is rounded to minutes, so the time the request takes doesn't
   * matter.
   */
  private List<Duration> readHowFarEveryPartitionIsAhead() {
    return IntStream.range(0, PARTITION_COUNT)
        .mapToObj(
            i -> {
              final Instant clockTime =
                  client.newClockGetRequest().send().join().getTimestamp().toInstant();
              final long aheadMillis = Duration.between(Instant.now(), clockTime).toMillis();
              return Duration.ofMinutes(Math.round(aheadMillis / 60_000.0));
            })
        .toList();
  }
}

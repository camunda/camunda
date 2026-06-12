/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.zeebe.LoadTesterApplication;
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the starter loop self-terminates when {@code duration-limit} elapses, leaving {@code
 * starter.run.finished} at 1. The {@code CommandLineRunner} returns once the broker is connected
 * and the process deployed (so the creation loop runs in the background while context startup
 * completes), so the test body polls with Awaitility until the loop finishes. Excludes the {@code
 * worker} profile to avoid the known {@code @SpringBootTest} hang.
 */
@Testcontainers
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.starter.rate=2",
      "load-tester.starter.duration-limit=3",
      "load-tester.starter.threads=1",
      "load-tester.starter.payload-path=bpmn/small_payload.json",
      "load-tester.monitor-data-availability=false",
      "load-tester.perform-read-benchmarks=false",
    })
@ActiveProfiles({"starter", "it"})
@Disabled
class StarterDurationLimitIT {

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @Autowired private MeterRegistry meterRegistry;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldStopAfterDurationLimit() {
    // given — the starter is creating instances in the background; the CommandLineRunner returned
    //         once connected + deployed, so we poll until the duration-limit (3s) elapses.

    // then — the run-finished gauge should reach 1, proving the loop exited cleanly via the
    //        deadline (not via cancellation or an error path).
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              final var runFinishedGauge =
                  meterRegistry.find(StarterMetricsDoc.RUN_FINISHED.getName()).gauge();
              assertThat(runFinishedGauge)
                  .describedAs("starter.run.finished gauge should be registered")
                  .isNotNull();
              assertThat(runFinishedGauge.value())
                  .describedAs("gauge should be 1 after the duration-limit elapsed")
                  .isEqualTo(1.0);
            });

    // and — at least one start request was submitted, ruling out an immediate stop.
    final var counter =
        meterRegistry.find(StarterMetricsDoc.PROCESS_INSTANCES_STARTED.getName()).counter();
    assertThat(counter)
        .describedAs("starter.process.instances.started counter should be registered")
        .isNotNull();
    assertThat(counter.count())
        .describedAs("counter should reflect that the loop ran at least once before stopping")
        .isGreaterThan(0.0);
  }
}

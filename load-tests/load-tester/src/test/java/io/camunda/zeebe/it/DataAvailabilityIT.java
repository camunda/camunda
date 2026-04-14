/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.zeebe.LoadTesterApplication;
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the data availability monitoring. Verifies that the starter can measure how
 * long it takes for a created process instance to become queryable via the search API.
 *
 * <p>This test relies on the {@link CamundaContainer}'s built-in H2 database and RDBMS exporter,
 * which flush immediately (PT0S), making process instances queryable almost instantly.
 *
 * <p>The starter runs for {@code duration-limit} seconds (blocking Spring Boot startup). During
 * that time, it creates instances and the {@link io.camunda.zeebe.ProcessInstanceStartMeter}
 * periodically checks if they are queryable. By the time this test method executes, the data
 * availability latency metric should have recordings.
 */
@Testcontainers
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.starter.rate=1",
      "load-tester.starter.duration-limit=30",
      "load-tester.starter.threads=1",
      "load-tester.starter.payload-path=bpmn/small_payload.json",
      "load-tester.monitor-data-availability=true",
      "load-tester.monitor-data-availability-interval=500ms",
      "load-tester.perform-read-benchmarks=false",
    })
@ActiveProfiles({"starter", "it"})
class DataAvailabilityIT {

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void shouldMeasureDataAvailabilityLatency() {
    // given - starter has run for duration-limit seconds with data availability monitoring

    // then - verify that data availability latency was recorded
    final var timer =
        meterRegistry.find(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName()).timer();
    assertThat(timer)
        .describedAs("Data availability latency timer should be registered")
        .isNotNull();
    assertThat(timer.count())
        .describedAs("At least one data availability measurement should be recorded")
        .isGreaterThan(0);
  }
}

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
 * Integration test for the Starter component. Verifies that the starter can connect to a Camunda
 * cluster, deploy a BPMN process, and create process instances.
 *
 * <p>The Starter runs as a {@link org.springframework.boot.CommandLineRunner} and blocks the Spring
 * Boot startup thread until the configured {@code duration-limit} expires. By the time this test
 * method executes, the starter has already finished creating process instances and the recorded
 * metrics are available in the registry.
 */
@Testcontainers
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.starter.rate=1",
      "load-tester.starter.duration-limit=10",
      "load-tester.starter.threads=1",
      "load-tester.starter.payload-path=bpmn/small_payload.json",
      "load-tester.monitor-data-availability=false",
      "load-tester.perform-read-benchmarks=false",
    })
@ActiveProfiles({"starter", "it"})
class StarterIT {

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void shouldCreateProcessInstances() {
    // given - starter has run for duration-limit seconds before this test method executes

    // then - verify that process instances were created by checking the response latency metric
    final var timer =
        meterRegistry.find(StarterLatencyMetricsDoc.RESPONSE_LATENCY.getName()).timer();
    assertThat(timer).describedAs("Response latency timer should be registered").isNotNull();
    assertThat(timer.count())
        .describedAs("Starter should have created at least one process instance")
        .isGreaterThan(0);
  }
}

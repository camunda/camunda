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
import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration test activating both the {@code starter} and {@code worker} profiles in
 * the same Spring context. The starter deploys the BPMN, creates a small number of instances at a
 * low rate, and the worker completes them via {@code @JobWorker}. The test then queries the broker
 * directly to confirm that at least one process instance reached the {@code COMPLETED} state.
 */
@Testcontainers
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      // starter: create a handful of instances for a short window
      "load-tester.starter.rate=1",
      "load-tester.starter.duration-limit=5",
      "load-tester.starter.threads=1",
      "load-tester.starter.payload-path=bpmn/small_payload.json",
      // worker: no artificial delay; small payload to keep the IT fast
      "load-tester.worker.completion-delay=0ms",
      "load-tester.worker.payload-path=bpmn/small_payload.json",
      // avoid background meters hitting the testcontainer gateway
      "load-tester.monitor-data-availability=false",
    })
@ActiveProfiles({"starter", "worker", "it"})
class StarterWorkerIT {

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @Autowired private ZeebeClient client;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldStartInstancesAndCompleteThem() {
    // given - the starter has already run (CommandLineRunner blocks context startup for
    //         duration-limit=5s) and produced ~5 instances of the "benchmark" process.
    //         The worker is also active and subscribed to "benchmark-task".

    // then - at least one instance should be queryable and in COMPLETED state.
    //        Awaitility handles the async gap between "starter stopped" and "worker drained queue".
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response =
                  client
                      .newProcessInstanceQuery()
                      .filter(f -> f.bpmnProcessId("benchmark"))
                      .send()
                      .join();

              assertThat(response.items())
                  .describedAs("Starter should have created instances of 'benchmark'")
                  .isNotEmpty();

              assertThat(response.items())
                  .describedAs("Worker should have completed at least one instance")
                  .anyMatch(pi -> "COMPLETED".equals(pi.getState()));
            });
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * Verifies end-to-end (real gRPC + real broker) that a worker configured with a short {@code
 * streamInactivityTimeout} has its stream cancelled and recreated after the inactivity window, and
 * continues to receive jobs across the recreation. See issue #44264.
 */
@ZeebeIntegration
final class JobStreamerInactivityTimeoutIT {

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker zeebe;

  @AutoClose private final CamundaClient client = zeebe.newClientBuilder().build();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    zeebe = new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();
  }

  @Test
  void shouldRecreateStreamAfterInactivityAndKeepReceivingJobs() {
    // given
    final var jobType = Strings.newRandomValidBpmnId();
    final var receivedJobs = new CopyOnWriteArrayList<ActivatedJob>();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(jobType)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(jobType))
            .endEvent()
            .done();
    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    final var inactivityTimeout = Duration.ofSeconds(1);

    try (final JobWorker ignored =
        client
            .newWorker()
            .jobType(jobType)
            .handler((c, j) -> receivedJobs.add(j))
            .streamEnabled(true)
            .streamInactivityTimeout(inactivityTimeout)
            .streamTimeout(Duration.ofHours(1))
            .open()) {

      // when - collect stream ids over time. An idle worker triggers recreation every
      // `inactivityTimeout` via the inactivity watchdog, so the set should grow beyond 1.
      final Set<String> observedStreamIds = ConcurrentHashMap.newKeySet();
      final JobStreamActuator jobStreamActuator = JobStreamActuator.of(zeebe);
      Awaitility.await("until inactivity-triggered recreation is observed")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                final List<String> streamIdsForJobType =
                    jobStreamActuator.listClient().stream()
                        .filter(s -> jobType.equals(s.jobType()))
                        .map(s -> s.id().toString())
                        .toList();
                observedStreamIds.addAll(streamIdsForJobType);
                assertThat(observedStreamIds)
                    .as("worker should have opened multiple streams via inactivity timeout")
                    .hasSizeGreaterThan(1);
              });

      // when - a job is created after the stream has been recreated
      client.newCreateInstanceCommand().bpmnProcessId(jobType).latestVersion().send().join();

      // then - the worker still receives it, proving the recreation is transparent end-to-end
      Awaitility.await("until job is received after stream recreation")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(receivedJobs).hasSize(1));
    }
  }
}

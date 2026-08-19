/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * Verifies end-to-end (real gRPC + real broker) that a streaming worker opted into {@code
 * withLease} receives a lease token on each activated job, and that the token fences job completion
 * the same way it does on the poll path (see {@link CompleteJobTest}).
 */
@ZeebeIntegration
final class StreamJobsWithLeaseIT {

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker zeebe;

  @AutoClose private final CamundaClient client = zeebe.newClientBuilder().build();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    zeebe = new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();
  }

  @Test
  void shouldCarryLeaseTokenOnStreamedJobAndAllowCompletionWithIt() {
    // given
    final var jobType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(jobType)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(jobType))
            .endEvent()
            .done();
    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    final var receivedJobs = new CopyOnWriteArrayList<ActivatedJob>();
    try (final JobWorker ignored =
        client
            .newWorker()
            .jobType(jobType)
            .handler((c, j) -> receivedJobs.add(j))
            .streamEnabled(true)
            .withLease(true)
            .open()) {
      // when
      awaitStreamRegistered(jobType);
      client.newCreateInstanceCommand().bpmnProcessId(jobType).latestVersion().send().join();
      Awaitility.await("until the streamed job is received")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(receivedJobs).hasSize(1));
      final ActivatedJob job = receivedJobs.get(0);

      // then
      assertThat(job.getLeaseToken())
          .describedAs("Expected the streamed job to carry a lease token")
          .isNotEmpty();

      // completing with the lease token carried on the job must succeed
      client.newCompleteCommand(job).send().join();
    }
  }

  @Test
  void shouldRejectCompletingStreamedLeasedJobWithoutMatchingLeaseToken() {
    // given
    final var jobType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(jobType)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(jobType))
            .endEvent()
            .done();
    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    final var receivedJobs = new CopyOnWriteArrayList<ActivatedJob>();
    try (final JobWorker ignored =
        client
            .newWorker()
            .jobType(jobType)
            .handler((c, j) -> receivedJobs.add(j))
            .streamEnabled(true)
            .withLease(true)
            .open()) {
      // when
      awaitStreamRegistered(jobType);
      client.newCreateInstanceCommand().bpmnProcessId(jobType).latestVersion().send().join();
      Awaitility.await("until the streamed job is received")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(receivedJobs).hasSize(1));
      final ActivatedJob job = receivedJobs.get(0);
      assertThat(job.getLeaseToken())
          .describedAs("Expected the streamed job to carry a lease token")
          .isNotEmpty();

      // when / then - completing by job key alone carries no lease token, so a leased job must
      // reject it the same way the poll path does
      final var expectedMessage =
          String.format(
              "Expected to process job with key '%d', but a matching lease token must be provided "
                  + "because the job is currently leased",
              job.getKey());
      assertThatThrownBy(() -> client.newCompleteCommand(job.getKey()).send().join())
          .hasMessageContaining(expectedMessage);
    }
  }

  private void awaitStreamRegistered(final String jobType) {
    final var actuator = JobStreamActuator.of(zeebe);
    Awaitility.await("until stream with type '%s' is registered".formatted(jobType))
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .haveJobType(1, jobType));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.waitstate;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.JobWaitStateDetails;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a job parked while its secret cannot be resolved is distinguishable in the wait
 * state from an ordinary unclaimed job (#63191): its {@link JobWaitStateDetails} carries {@code
 * waitingForSecretResolution == true} while parked, and reverts to {@code false} once the job is
 * un-parked.
 *
 * <p>The store is configured but the referenced secret is intentionally absent, so the first
 * activation parks the job and the background resolution fails and raises a {@code
 * SECRET_RESOLUTION_ERROR} incident. That leaves the job stably parked long enough to assert the
 * exported flag across the exporter round-trip. A retries update in between keeps the flag, since
 * the engine parks the job again right after it. Writing the secret and resolving the incident then
 * un-parks the job, which flips the flag back.
 *
 * <p>A job whose secret resolves in the background is un-parked by the reactivation instead, which
 * pushes it to a job stream when one is registered. That path is covered separately, since the push
 * activates the job and the wait-state exporter reads no activation records.
 */
@MultiDbTest
public class WaitStateSecretResolutionIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          // the store exists but is empty, so the referenced secret resolves to NOT_FOUND
          .withFileBasedSecretStore(directory -> {});

  private static final String SECRET_VALUE = "resolved-secret-value";

  private static CamundaClient camundaClient;

  @Test
  void shouldMarkAndUnmarkJobWaitStateOnSecretResolution() {
    // given — a service task whose input mapping references a secret the store does not hold
    final String secretName = uniqueSecretName("MY_SECRET");
    final String processId = "waitStateSecretProcess";
    final String jobType = "secret-consumer";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                "secret-task",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeInputExpression("camunda.secrets." + secretName, "authorization"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateSecretProcess.bpmn");

    final long pik = startProcessInstance(camundaClient, processId).getProcessInstanceKey();

    // when — a one-shot activation attempt parks the job and requests the secret resolution
    camundaClient
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(1)
        .requestTimeout(Duration.ofSeconds(1))
        .send()
        .join();

    // and — the background resolution fails against the empty store, raising an incident that
    // holds the job stably parked
    final Incident incident =
        Awaitility.await("secret-resolution incident should be raised for the parked job")
            .atMost(TIMEOUT_DATA_AVAILABILITY)
            .until(
                () ->
                    camundaClient
                        .newIncidentSearchRequest()
                        .filter(
                            f ->
                                f.processInstanceKey(pik)
                                    .errorType(IncidentErrorType.SECRET_RESOLUTION_ERROR)
                                    .state(IncidentState.ACTIVE))
                        .send()
                        .join()
                        .items()
                        .stream()
                        .findFirst()
                        .orElse(null),
                found -> found != null);

    // then — the wait state marks the job as secret-parked
    Awaitility.await("wait state should be flagged as secret-parked while the job is parked")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> assertThat(jobWaitStateDetails(pik).isWaitingForSecretResolution()).isTrue());

    // when - the retries of the job the incident holds are updated
    final long jobKey = Long.parseLong(jobWaitStateDetails(pik).getJobKey());
    camundaClient.newUpdateRetriesCommand(jobKey).retries(5).send().join();

    // then - the wait state takes the new retries and keeps the mark, since the engine parks the
    // job again right after the retries update
    Awaitility.await("wait state should keep the secret-wait mark across a retries update")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final JobWaitStateDetails details = jobWaitStateDetails(pik);
              assertThat(details.getRetries()).isEqualTo(5);
              assertThat(details.isWaitingForSecretResolution()).isTrue();
            });

    // when — the secret becomes available and the incident is resolved, un-parking the job
    try {
      Files.writeString(
          BROKER.getFileBasedSecretStoreDirectory().resolve(secretName),
          SECRET_VALUE,
          StandardCharsets.UTF_8);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to write the secret into the file-based store", e);
    }
    camundaClient.newResolveIncidentCommand(incident.getIncidentKey()).send().join();

    // then — the wait state reverts to a plain job wait
    Awaitility.await("wait state should no longer be flagged as secret-parked once un-parked")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> assertThat(jobWaitStateDetails(pik).isWaitingForSecretResolution()).isFalse());

    // cleanup — cancel the instance so the wait state does not leak into other tests
    camundaClient.newCancelInstanceCommand(pik).execute();
    waitForProcessInstanceToBeTerminated(camundaClient, pik);
  }

  @Test
  void shouldUnmarkJobWaitStateOnceTheResolvedJobIsPushedToAStream() throws Exception {
    // given - a secret the store holds but nothing has read yet, so its value is not cached and a
    // job referencing it is parked until the background resolution caches it
    final String secretName = uniqueSecretName("STREAMED_SECRET");
    Files.writeString(
        BROKER.getFileBasedSecretStoreDirectory().resolve(secretName),
        SECRET_VALUE,
        StandardCharsets.UTF_8);
    final String jobType = "streamed-secret-consumer";
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess("waitStateStreamedSecretProcess")
            .startEvent()
            .serviceTask(
                "streamed-secret-task",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeInputExpression("camunda.secrets." + secretName, "authorization"))
            .endEvent()
            .done(),
        "waitStateStreamedSecretProcess.bpmn");

    // and - a job stream that keeps every job pushed to it without completing it, registered
    // before the job exists so the job is pushed rather than polled
    final List<ActivatedJob> streamed = new CopyOnWriteArrayList<>();
    final var stream =
        camundaClient
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(streamed::add)
            .timeout(Duration.ofMinutes(10))
            .send();
    final long pik;
    try {
      awaitStreamRegistered(jobType);

      // when - the job parks on the uncached secret, the resolution caches it and the
      // reactivation pushes the job to the stream
      pik =
          startProcessInstance(camundaClient, "waitStateStreamedSecretProcess")
              .getProcessInstanceKey();
      Awaitility.await("the resolved job should be pushed to the stream")
          .atMost(TIMEOUT_DATA_AVAILABILITY)
          .untilAsserted(() -> assertThat(streamed).hasSize(1));
      assertThat(streamed.getFirst().getVariablesAsMap())
          .containsEntry("authorization", SECRET_VALUE);

      // and - a later wait state on the same partition is visible, so every record written before
      // it, the pushed job's park and hand-out included, is exported too. Without it the check
      // below could read the wait state from before the park, which is not marked either
      awaitWaitStateExportedAfterThePush();

      // then - the job was parked on its uncached secret and resumed before the push, so the check
      // below covers a job that was marked, not one pushed right away
      final long jobKey = streamed.getFirst().getKey();
      assertThat(
              RecordingExporter.jobRecords(JobIntent.SECRET_RESOLUTION_PARKED)
                  .withRecordKey(jobKey)
                  .exists())
          .isTrue();
      assertThat(
              RecordingExporter.jobRecords(JobIntent.SECRET_RESOLUTION_RESUMED)
                  .withRecordKey(jobKey)
                  .exists())
          .isTrue();

      // and - the pushed job, activated and held by the stream, no longer reports a secret wait
      assertThat(jobWaitStateDetails(pik).isWaitingForSecretResolution()).isFalse();
    } finally {
      stream.cancel(true);
    }

    // cleanup - cancel the instance so the wait state does not leak into other tests
    camundaClient.newCancelInstanceCommand(pik).execute();
    waitForProcessInstanceToBeTerminated(camundaClient, pik);
  }

  /**
   * Returns a secret name no earlier run used. The store outlives a single test, so a rerun that
   * reused a name would find the secret there already and never park its job.
   */
  private static String uniqueSecretName(final String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  /** Returns the details of the single job wait state of the process instance. */
  private static JobWaitStateDetails jobWaitStateDetails(final long processInstanceKey) {
    final var items =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join()
            .items();
    assertThat(items).hasSize(1);
    assertThat(items.getFirst().getDetails()).isInstanceOf(JobWaitStateDetails.class);
    return (JobWaitStateDetails) items.getFirst().getDetails();
  }

  private static void awaitStreamRegistered(final String jobType) {
    final var actuator = JobStreamActuator.of(BROKER);
    Awaitility.await("until a stream with type '%s' is registered".formatted(jobType))
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .haveJobType(1, jobType));
  }

  /**
   * Starts an instance waiting on a plain job and waits until its wait state is searchable. The
   * broker runs a single partition, which exports its records in order, so everything written
   * before the instance was started is exported by then.
   */
  private static void awaitWaitStateExportedAfterThePush() {
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess("waitStateExportSentinelProcess")
            .startEvent()
            .serviceTask("sentinel-task", t -> t.zeebeJobType("export-sentinel"))
            .endEvent()
            .done(),
        "waitStateExportSentinelProcess.bpmn");
    final long sentinelKey =
        startProcessInstance(camundaClient, "waitStateExportSentinelProcess")
            .getProcessInstanceKey();
    Awaitility.await("the sentinel wait state should be exported")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(sentinelKey))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
    camundaClient.newCancelInstanceCommand(sentinelKey).execute();
    waitForProcessInstanceToBeTerminated(camundaClient, sentinelKey);
  }
}

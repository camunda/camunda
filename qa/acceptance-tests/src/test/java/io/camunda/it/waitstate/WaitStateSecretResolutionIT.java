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
import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.JobWaitStateDetails;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a job parked while its secret cannot be resolved is distinguishable in the wait
 * state from an ordinary unclaimed job (#63191): its {@link JobWaitStateDetails} carries {@code
 * secretResolutionPending == true} while parked, and reverts to {@code false} once the job is
 * un-parked.
 *
 * <p>The store is configured but the referenced secret is intentionally absent, so the first
 * activation parks the job and the background resolution fails and raises a {@code
 * SECRET_RESOLUTION_ERROR} incident. That leaves the job stably parked long enough to assert the
 * exported flag across the exporter round-trip. Writing the secret and resolving the incident then
 * un-parks the job, which flips the flag back.
 */
@MultiDbTest
public class WaitStateSecretResolutionIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      // the store exists but is empty, so the referenced secret resolves to NOT_FOUND
      new TestStandaloneBroker().withFileBasedSecretStore(directory -> {});

  private static final String SECRET_NAME = "MY_SECRET";
  private static final String SECRET_VALUE = "resolved-secret-value";

  private static CamundaClient camundaClient;

  @Test
  void shouldMarkAndUnmarkJobWaitStateOnSecretResolution() {
    // given — a service task whose input mapping references a secret the store does not hold
    final String processId = "waitStateSecretProcess";
    final String jobType = "secret-consumer";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                "secret-task",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeInputExpression("camunda.secrets." + SECRET_NAME, "authorization"))
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
            () -> {
              final var items =
                  camundaClient
                      .newElementInstanceWaitStateSearchRequest()
                      .filter(f -> f.processInstanceKey(pik))
                      .send()
                      .join()
                      .items();
              assertThat(items).hasSize(1);
              assertThat(items.getFirst().getDetails()).isInstanceOf(JobWaitStateDetails.class);
              assertThat(
                      ((JobWaitStateDetails) items.getFirst().getDetails())
                          .getSecretResolutionPending())
                  .isTrue();
            });

    // when — the secret becomes available and the incident is resolved, un-parking the job
    try {
      Files.writeString(
          BROKER.getFileBasedSecretStoreDirectory().resolve(SECRET_NAME),
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
            () -> {
              final var items =
                  camundaClient
                      .newElementInstanceWaitStateSearchRequest()
                      .filter(f -> f.processInstanceKey(pik))
                      .send()
                      .join()
                      .items();
              assertThat(items).hasSize(1);
              assertThat(items.getFirst().getDetails()).isInstanceOf(JobWaitStateDetails.class);
              assertThat(
                      ((JobWaitStateDetails) items.getFirst().getDetails())
                          .getSecretResolutionPending())
                  .isFalse();
            });

    // cleanup — cancel the instance so the wait state does not leak into other tests
    camundaClient.newCancelInstanceCommand(pik).execute();
    waitForProcessInstanceToBeTerminated(camundaClient, pik);
  }
}

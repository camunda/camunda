/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationUpdateJobIT {

  private static CamundaClient camundaClient;

  private static final int PROCESS_INSTANCE_COUNT = 3;

  String testScopeId;
  final List<ProcessInstanceEvent> activeProcessInstances = new ArrayList<>();

  @BeforeEach
  void beforeEach(final TestInfo testInfo) {
    Objects.requireNonNull(camundaClient);
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    deployResource(camundaClient, "process/service_tasks_v1.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    for (int i = 0; i < PROCESS_INSTANCE_COUNT; i++) {
      activeProcessInstances.add(
          startScopedProcessInstance(camundaClient, "service_tasks_v1", testScopeId));
    }

    final List<Long> piKeys = piKeys();
    waitForJobs(
        camundaClient, f -> f.processInstanceKey(b -> b.in(piKeys)), PROCESS_INSTANCE_COUNT);
  }

  @AfterEach
  void afterEach() {
    activeProcessInstances.clear();
  }

  @Test
  void shouldUpdateJobPriorityWithBatch() {
    // given
    final List<Long> piKeys = piKeys();

    // when
    final Future<CreateBatchOperationResponse> result =
        camundaClient
            .newCreateBatchOperationCommand()
            .updateJob()
            .priority(99)
            .filter(f -> f.processInstanceKey(b -> b.in(piKeys)))
            .send();

    // then
    final String batchOperationKey =
        assertThat(result)
            .succeedsWithin(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
            .actual()
            .getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, PROCESS_INSTANCE_COUNT);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, PROCESS_INSTANCE_COUNT, 0);

    waitForJobs(
        camundaClient,
        f -> f.processInstanceKey(b -> b.in(piKeys)).priority(99),
        PROCESS_INSTANCE_COUNT);
  }

  @Test
  void shouldUpdateJobRetriesWithBatch() {
    // given
    final List<Long> piKeys = piKeys();

    // when
    final Future<CreateBatchOperationResponse> result =
        camundaClient
            .newCreateBatchOperationCommand()
            .updateJob()
            .retries(7)
            .filter(f -> f.processInstanceKey(b -> b.in(piKeys)))
            .send();

    // then
    final String batchOperationKey =
        assertThat(result)
            .succeedsWithin(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
            .actual()
            .getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, PROCESS_INSTANCE_COUNT);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, PROCESS_INSTANCE_COUNT, 0);

    waitForJobs(
        camundaClient,
        f -> f.processInstanceKey(b -> b.in(piKeys)).retries(7),
        PROCESS_INSTANCE_COUNT);
  }

  @Test
  void shouldUpdateJobTimeoutWithBatch() {
    // given - activate jobs first so they have a deadline (engine requires an existing deadline
    // to update the timeout via UPDATE_JOB)
    final List<Long> piKeys = piKeys();
    camundaClient
        .newActivateJobsCommand()
        .jobType("taskA")
        .maxJobsToActivate(100)
        .workerName("test-worker")
        .timeout(Duration.ofMinutes(5))
        .send()
        .join();

    // when
    final OffsetDateTime beforeBatchSend = OffsetDateTime.now();
    final Future<CreateBatchOperationResponse> result =
        camundaClient
            .newCreateBatchOperationCommand()
            .updateJob()
            .timeout(Duration.ofMinutes(30))
            .filter(f -> f.processInstanceKey(b -> b.in(piKeys)))
            .send();

    // then
    final String batchOperationKey =
        assertThat(result)
            .succeedsWithin(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
            .actual()
            .getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, PROCESS_INSTANCE_COUNT);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, PROCESS_INSTANCE_COUNT, 0);

    waitForJobs(
        camundaClient,
        f ->
            f.processInstanceKey(b -> b.in(piKeys))
                .deadline(d -> d.gte(beforeBatchSend.plusMinutes(25))),
        PROCESS_INSTANCE_COUNT);
  }

  @Test
  void shouldUpdateJobWithAllChangesetFields() {
    // given - activate jobs first so they have a deadline (engine requires an existing deadline
    // to update the timeout via UPDATE_JOB); changeset setters are called before filter() to
    // exercise the pre-filter stage of the builder
    final List<Long> piKeys = piKeys();
    camundaClient
        .newActivateJobsCommand()
        .jobType("taskA")
        .maxJobsToActivate(100)
        .workerName("test-worker")
        .timeout(Duration.ofMinutes(5))
        .send()
        .join();

    // when - priority/retries/timeout set before filter() to verify the pre-filter API stage
    final OffsetDateTime beforeBatchSend = OffsetDateTime.now();
    final Future<CreateBatchOperationResponse> result =
        camundaClient
            .newCreateBatchOperationCommand()
            .updateJob()
            .priority(75)
            .retries(5)
            .timeout(Duration.ofMinutes(30))
            .filter(f -> f.processInstanceKey(b -> b.in(piKeys)))
            .send();

    // then
    final String batchOperationKey =
        assertThat(result)
            .succeedsWithin(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
            .actual()
            .getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, PROCESS_INSTANCE_COUNT);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, PROCESS_INSTANCE_COUNT, 0);

    waitForJobs(
        camundaClient,
        f -> f.processInstanceKey(b -> b.in(piKeys)).priority(75),
        PROCESS_INSTANCE_COUNT);
    waitForJobs(
        camundaClient,
        f -> f.processInstanceKey(b -> b.in(piKeys)).retries(5),
        PROCESS_INSTANCE_COUNT);
    waitForJobs(
        camundaClient,
        f ->
            f.processInstanceKey(b -> b.in(piKeys))
                .deadline(d -> d.gte(beforeBatchSend.plusMinutes(25))),
        PROCESS_INSTANCE_COUNT);
  }

  @Test
  void shouldRejectUpdateJobWithEmptyChangeset() {
    // given
    final List<Long> piKeys = piKeys();

    // when / then - no changeset fields set; server returns 400 with
    // "No changeset provided."
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateBatchOperationCommand()
                    .updateJob()
                    .filter(f -> f.processInstanceKey(b -> b.in(piKeys)))
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("No changeset provided.");
  }

  @Test
  void shouldRejectUpdateJobWithEmptyFilter() {
    // when / then - filter consumer sets no criteria; server returns 400 with
    // "At least one of filter criteria is required."
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateBatchOperationCommand()
                    .updateJob()
                    .priority(99)
                    .filter(f -> {})
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("At least one of filter criteria is required");
  }

  private List<Long> piKeys() {
    return activeProcessInstances.stream()
        .map(ProcessInstanceEvent::getProcessInstanceKey)
        .collect(Collectors.toList());
  }
}

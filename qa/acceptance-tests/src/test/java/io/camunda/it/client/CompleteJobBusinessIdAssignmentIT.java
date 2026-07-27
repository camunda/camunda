/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies late Business ID assignment via {@code CompleteJob} end to end over both client
 * transports (ADR 0006, D11/D12): the happy path with propagation to secondary storage, that
 * re-assigning an already-assigned instance is rejected, and that a failed assignment rejects the
 * whole completion atomically, leaving the job open and the previously assigned business id
 * untouched.
 *
 * <p>Each test uses its own process/job type so that {@code activateNextJob} never steals a job
 * from a concurrently running test's instance — which would otherwise assign the business id to the
 * wrong instance.
 */
@MultiDbTest
class CompleteJobBusinessIdAssignmentIT {

  private static final int MAX_BUSINESS_ID_LENGTH = 256;

  private static CamundaClient camundaClient;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldAssignBusinessIdWhenCompletingJob(final boolean useRest) {
    // given - a running process instance that currently has no business id
    final var jobType = jobType("complete-assign", useRest);
    final long processInstanceKey = startProcessInstance(jobType);
    final var businessId = "order-" + transport(useRest) + "-98765";

    // when - the first job is completed with a business id to assign
    completeNextJob(jobType, businessId, useRest);

    // then - the assignment propagates to secondary storage
    awaitBusinessId(processInstanceKey, businessId);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRejectReassigningBusinessIdOnCompletion(final boolean useRest) {
    // given - a process instance that already has a business id assigned via job completion
    final var jobType = jobType("complete-reassign", useRest);
    final long processInstanceKey = startProcessInstance(jobType);
    completeNextJob(jobType, "order-original", useRest);
    awaitBusinessId(processInstanceKey, "order-original");

    // when / then - completing a job while assigning a different business id is rejected, leaving
    // the job open (the whole completion is atomic)
    final var job = activateNextJob(jobType);
    assertThatThrownBy(() -> completeJob(job, "order-different", useRest))
        .describedAs(
            "assigning a different business id while completing a job is rejected over %s",
            transport(useRest))
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("it already has a business id assigned");

    // and - re-sending the identical business id is likewise rejected, again leaving the job open
    assertThatThrownBy(() -> completeJob(job, "order-original", useRest))
        .describedAs(
            "re-sending the identical business id while completing a job is rejected over %s",
            transport(useRest))
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("it already has a business id assigned");

    // and - the job is still open and can be completed normally, keeping the original business id
    assertThatNoException()
        .describedAs("the rejected completions left the job open")
        .isThrownBy(() -> completeJob(job, null, useRest));
    awaitBusinessId(processInstanceKey, "order-original");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRejectBusinessIdExceedingMaxLengthOnCompletion(final boolean useRest) {
    // given - a running process instance with no business id
    final var jobType = jobType("complete-too-long", useRest);
    final long processInstanceKey = startProcessInstance(jobType);
    final var tooLong = "b".repeat(MAX_BUSINESS_ID_LENGTH + 1);

    // when / then - completing a job with an over-long business id is rejected atomically
    final var job = activateNextJob(jobType);
    assertThatThrownBy(() -> completeJob(job, tooLong, useRest))
        .describedAs(
            "assigning an over-long business id while completing a job is rejected over %s",
            transport(useRest))
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("exceeds the limit of 256 characters");

    // and - the job is still open and can be completed normally, with no business id assigned
    assertThatNoException()
        .describedAs("the rejected completion left the job open")
        .isThrownBy(() -> completeJob(job, null, useRest));
    await("no business id is assigned to the instance")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(getBusinessId(processInstanceKey)).isNullOrEmpty());
  }

  private void completeNextJob(
      final String jobType, final String businessId, final boolean useRest) {
    completeJob(activateNextJob(jobType), businessId, useRest);
  }

  private void completeJob(final ActivatedJob job, final String businessId, final boolean useRest) {
    final var command = camundaClient.newCompleteCommand(job.getKey());
    final var withTransport = useRest ? command.useRest() : command.useGrpc();
    if (businessId != null) {
      withTransport.withBusinessId(businessId);
    }
    withTransport.send().join();
  }

  private ActivatedJob activateNextJob(final String jobType) {
    return await("a job is available to activate")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () ->
                camundaClient
                    .newActivateJobsCommand()
                    .jobType(jobType)
                    .maxJobsToActivate(1)
                    .send()
                    .join()
                    .getJobs()
                    .stream()
                    .findFirst()
                    .orElse(null),
            job -> job != null);
  }

  private long startProcessInstance(final String jobType) {
    final var processId = jobType + "-process";
    deployProcessAndWaitForIt(
        camundaClient, twoJobProcess(processId, jobType), processId + ".bpmn");

    final long processInstanceKey =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(processInstanceKey),
        instances -> assertThat(instances).hasSize(1));

    return processInstanceKey;
  }

  private static BpmnModelInstance twoJobProcess(final String processId, final String jobType) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask("task-1", t -> t.zeebeJobType(jobType))
        .serviceTask("task-2", t -> t.zeebeJobType(jobType))
        .userTask("wait")
        .endEvent()
        .done();
  }

  private void awaitBusinessId(final long processInstanceKey, final String businessId) {
    await("business id is reflected in secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(getBusinessId(processInstanceKey)).isEqualTo(businessId));
  }

  private String getBusinessId(final long processInstanceKey) {
    return camundaClient
        .newProcessInstanceGetRequest(processInstanceKey)
        .send()
        .join()
        .getBusinessId();
  }

  private static String jobType(final String prefix, final boolean useRest) {
    return prefix + "-" + transport(useRest);
  }

  private static String transport(final boolean useRest) {
    return useRest ? "rest" : "grpc";
  }
}

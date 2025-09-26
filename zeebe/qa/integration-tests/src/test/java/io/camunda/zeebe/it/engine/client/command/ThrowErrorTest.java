/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ActivateJobsCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ThrowErrorResponse;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public final class ThrowErrorTest {

  private static final String ERROR_CODE = "error";
  @AutoClose CamundaClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  public void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldThrowError(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final long jobKey = activateJob(client, useRest, jobType).getKey();

    // when
    final ThrowErrorResponse response =
        getCommand(client, useRest, jobKey).errorCode(ERROR_CODE).send().join();

    // then
    assertThat(response).isNotNull().isInstanceOf(ThrowErrorResponse.class);

    final Record<JobRecordValue> record =
        jobRecords(JobIntent.ERROR_THROWN).withRecordKey(jobKey).getFirst();
    Assertions.assertThat(record.getValue()).hasErrorCode(ERROR_CODE).hasErrorMessage("");

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(record.getValue().getProcessInstanceKey())
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .exists())
        .isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldThrowErrorWithErrorMessage(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final long jobKey = activateJob(client, useRest, jobType).getKey();

    // when
    getCommand(client, useRest, jobKey).errorCode(ERROR_CODE).errorMessage("test").send().join();

    // then
    final Record<JobRecordValue> record =
        jobRecords(JobIntent.ERROR_THROWN).withRecordKey(jobKey).getFirst();
    Assertions.assertThat(record.getValue()).hasErrorMessage("test");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfJobIsAlreadyCompleted(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final long jobKey = activateJob(client, useRest, jobType).getKey();
    getCompleteCommand(client, useRest, jobKey).send().join();

    // when
    final var expectedMessage =
        String.format(
            "Expected to throw an error for job with key '%d', but no such job was found", jobKey);

    assertThatThrownBy(
            () -> getCommand(client, useRest, jobKey).errorCode(ERROR_CODE).send().join())
        .hasMessageContaining(expectedMessage);
  }

  private ActivatedJob activateJob(
      final CamundaClient client, final boolean useRest, final String jobType) {
    final var activateResponse =
        getActivateCommand(client, useRest).jobType(jobType).maxJobsToActivate(1).send().join();

    assertThat(activateResponse.getJobs())
        .describedAs("Expected one job to be activated")
        .hasSize(1);

    return activateResponse.getJobs().get(0);
  }

  private ThrowErrorCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest, final long jobKey) {
    final ThrowErrorCommandStep1 throwErrorCommandStep1 = client.newThrowErrorCommand(jobKey);
    return useRest ? throwErrorCommandStep1.useRest() : throwErrorCommandStep1.useGrpc();
  }

  private ActivateJobsCommandStep1 getActivateCommand(
      final CamundaClient client, final boolean useRest) {
    final ActivateJobsCommandStep1 activateJobsCommandStep1 = client.newActivateJobsCommand();
    return useRest ? activateJobsCommandStep1.useRest() : activateJobsCommandStep1.useGrpc();
  }

  private CompleteJobCommandStep1 getCompleteCommand(
      final CamundaClient client, final boolean useRest, final long jobKey) {
    final CompleteJobCommandStep1 completeJobCommandStep1 = client.newCompleteCommand(jobKey);
    return useRest ? completeJobCommandStep1.useRest() : completeJobCommandStep1.useGrpc();
  }

  private void createProcessInstance(final String jobType) {
    final var processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .boundaryEvent("error", b -> b.error(ERROR_CODE).endEvent())
                .endEvent()
                .done());
    resourcesHelper.createProcessInstance(processDefinitionKey);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteJobCommandStep1.CompleteJobCommandJobResultStep;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class OperationReferenceTest {
  private static final long OPERATION_REFERENCE = 1234L;

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker zeebe;

  @AutoClose
  private final CamundaClient client =
      zeebe
          .newClientBuilder()
          .preferRestOverGrpc(false)
          .defaultRequestTimeout(Duration.ofMinutes(2))
          .build();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    zeebe = new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();
  }

  @Test
  void shouldIncludeOperationReferenceInExportedCommandRecord() {
    // Given
    final ZeebeResourcesHelper helper = new ZeebeResourcesHelper(client);
    final var modelInstance = helper.createSingleJobModelInstance("test", c -> {});
    final long processDefinitionKey = helper.deployProcess(modelInstance);
    final long processInstanceKey = helper.createProcessInstance(processDefinitionKey);

    // When
    client
        .newCancelInstanceCommand(processInstanceKey)
        .operationReference(OPERATION_REFERENCE)
        .send()
        .join();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .withRecordKey(processInstanceKey)
                .getFirst())
        .describedAs("Should contain client operationReference")
        .hasOperationReference(OPERATION_REFERENCE);
  }

  @Test
  void shouldIncludeOperationReferenceInFollowUpRecords() {
    // Given
    final ZeebeResourcesHelper helper = new ZeebeResourcesHelper(client);
    final var modelInstance = helper.createSingleJobModelInstance("test", c -> {});
    final long processDefinitionKey = helper.deployProcess(modelInstance);
    final long processInstanceKey = helper.createProcessInstance(processDefinitionKey);

    // When
    client
        .newCancelInstanceCommand(processInstanceKey)
        .operationReference(OPERATION_REFERENCE)
        .send()
        .join();

    // Then
    final var cancelCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
            .withRecordKey(processInstanceKey)
            .getFirst();

    final var followUpRecords =
        RecordingExporter.records()
            .withSourceRecordPosition(cancelCommand.getPosition())
            .limit(
                r ->
                    r.getKey() == processInstanceKey
                        && r.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATED);

    assertThat(followUpRecords)
        .hasSizeGreaterThan(0)
        .describedAs("Should contain client operationReference")
        .allSatisfy(r -> Assertions.assertThat(r).hasOperationReference(OPERATION_REFERENCE));
  }

  @Test
  void shouldHaveNegativeOneAsOperationReferenceIfNotSpecifiedInCommand() {
    // Given
    final ZeebeResourcesHelper helper = new ZeebeResourcesHelper(client);
    final var modelInstance = helper.createSingleJobModelInstance("test", c -> {});
    final long processDefinitionKey = helper.deployProcess(modelInstance);
    final long processInstanceKey = helper.createProcessInstance(processDefinitionKey);

    // When
    client.newCancelInstanceCommand(processInstanceKey).send().join();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .withRecordKey(processInstanceKey)
                .getFirst())
        .describedAs("Should contain -1 operationReference")
        .hasOperationReference(-1);
  }

  @Test
  void shouldAcceptNegativeOneAsOperationReference() {
    // Given
    final ZeebeResourcesHelper helper = new ZeebeResourcesHelper(client);
    final var modelInstance = helper.createSingleJobModelInstance("test", c -> {});
    final long processDefinitionKey = helper.deployProcess(modelInstance);
    final long processInstanceKey = helper.createProcessInstance(processDefinitionKey);

    // When
    client.newCancelInstanceCommand(processInstanceKey).operationReference(-1).send().join();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .withRecordKey(processInstanceKey)
                .getFirst())
        .describedAs("Should contain client operationReference")
        .hasOperationReference(-1);
  }

  @Test
  void shouldPreserveOperationRefOnElementTerminatedAfterCancelingUserTaskWithCancelingListeners() {
    // Given
    final var helper = new ZeebeResourcesHelper(client);
    final var listenerType = "canceling_listener";

    final var userTaskKey =
        helper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.canceling().type(listenerType)));

    final var processInstanceKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withRecordKey(userTaskKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    client
        .newWorker()
        .jobType(listenerType)
        .handler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .withResult(CompleteJobCommandJobResultStep::forUserTask)
                    .send()
                    .join())
        .open();

    // When
    client
        .newCancelInstanceCommand(processInstanceKey)
        .operationReference(OPERATION_REFERENCE)
        .execute();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withRecordKey(processInstanceKey)
                .getFirst())
        .describedAs("PI:ELEMENT_TERMINATED should carry client operationReference")
        .hasOperationReference(OPERATION_REFERENCE);
  }

  @Test
  void shouldPreserveOperationReferenceOnVariableUpdatedEventAfterUpdatingUserTaskWithListeners() {
    // Given
    final var helper = new ZeebeResourcesHelper(client);
    final var listenerType = "updating_listener";

    final var userTaskKey =
        helper.createSingleUserTask(t -> t.zeebeTaskListener(l -> l.updating().type(listenerType)));

    final var userTaskInstanceKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withRecordKey(userTaskKey)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    client
        .newWorker()
        .jobType(listenerType)
        .handler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .withResult(CompleteJobCommandJobResultStep::forUserTask)
                    .send()
                    .join())
        .open();

    // When
    client
        .newSetVariablesCommand(userTaskInstanceKey)
        .variables(Map.of("approvalStatus", "APPROVED"))
        .operationReference(OPERATION_REFERENCE)
        .execute();

    // Then
    Assertions.assertThat(
            RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATED)
                .withScopeKey(userTaskInstanceKey)
                .getFirst())
        .describedAs("VARIABLE_DOCUMENT:UPDATED should carry client operationReference")
        .hasOperationReference(OPERATION_REFERENCE);
  }

  @Test
  void shouldPreserveOperationRefOnVariableUpdateDeniedEventAfterDenialByUpdatingTaskListener() {
    // Given
    final var helper = new ZeebeResourcesHelper(client);
    final var listenerType = "updating_listener_with_denial";

    final var userTaskKey =
        helper.createSingleUserTask(t -> t.zeebeTaskListener(l -> l.updating().type(listenerType)));

    final var userTaskInstanceKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withRecordKey(userTaskKey)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    client
        .newWorker()
        .jobType(listenerType)
        .handler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .withResult(r -> r.forUserTask().deny(true).deniedReason("Denied by listener"))
                    .send()
                    .join())
        .open();

    // When
    client
        .newSetVariablesCommand(userTaskInstanceKey)
        .useRest()
        .variables(Map.of("approvalStatus", "APPROVED"))
        .local(true)
        .operationReference(OPERATION_REFERENCE)
        .send();

    // Then
    Assertions.assertThat(
            RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATE_DENIED)
                .withScopeKey(userTaskInstanceKey)
                .getFirst())
        .describedAs("VARIABLE_DOCUMENT:UPDATE_DENIED should carry client operationReference")
        .hasOperationReference(OPERATION_REFERENCE);
  }
}

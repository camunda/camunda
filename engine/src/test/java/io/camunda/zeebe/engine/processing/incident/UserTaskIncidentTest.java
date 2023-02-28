/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ZeebeUserTaskPropertiesBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValueAssert;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** Tests for incidents specific for user-tasks. */
public class UserTaskIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String TASK_ELEMENT_ID = "user-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance processWithUserTask(
      final Consumer<ZeebeUserTaskPropertiesBuilder<?>> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .userTask(TASK_ELEMENT_ID, modifier::accept)
        .endEvent()
        .done();
  }

  private IncidentRecordValueAssert assertIncidentCreated(
      final long processInstanceKey, final long elementInstanceKey) {
    final var incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    return Assertions.assertThat(incidentRecord.getValue())
        .hasElementId(TASK_ELEMENT_ID)
        .hasElementInstanceKey(elementInstanceKey)
        .hasJobKey(-1L)
        .hasVariableScopeKey(elementInstanceKey);
  }

  // --------------------------------------------------------------------------
  // ----- AssignmentDefinition related tests

  @Test
  public void shouldCreateIncidentIfAssigneeExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(u -> u.zeebeAssigneeExpression("MISSING_VAR")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'MISSING_VAR': no variable found for name 'MISSING_VAR'");
  }

  @Test
  public void shouldCreateIncidentIfAssigneeExpressionOfInvalidType() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(u -> u.zeebeAssigneeExpression("[1,2,3]")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression '[1,2,3]' to be 'STRING', but was 'ARRAY'.");
  }

  @Test
  public void shouldResolveIncidentAndCreateNewIncidentWhenContinuationFails() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(t -> t.zeebeAssigneeExpression("MISSING_VAR")))
        .deploy();

    // and an instance of that process is created without a variable for the assignee expression
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // and an incident created
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when we try to resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(incidentCreated.getKey())
                .exists())
        .describedAs("original incident is resolved")
        .isTrue();

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(i -> i.getKey() != incidentCreated.getKey())
                .exists())
        .describedAs("a new incident is created")
        .isTrue();
  }

  @Test
  public void shouldResolveIncidentAfterAssigneeExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(t -> t.zeebeAssigneeExpression("MISSING_VAR")))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("MISSING_VAR", "no longer missing")))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ELEMENT_ID)
                .exists())
        .isTrue();

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }

  @Test
  public void shouldCreateIncidentIfCandidateGroupsExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(u -> u.zeebeCandidateGroupsExpression("MISSING_VAR")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'MISSING_VAR': no variable found for name 'MISSING_VAR'");
  }

  @Test
  public void shouldCreateIncidentIfCandidateGroupsExpressionOfInvalidType() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithUserTask(u -> u.zeebeCandidateGroupsExpression("\"not a list\"")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression '\"not a list\"' to be 'ARRAY', but was 'STRING'.");
  }

  @Test
  public void shouldCreateIncidentIfCandidateGroupsExpressionOfInvalidArrayItemType() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(u -> u.zeebeCandidateGroupsExpression("[1,2,3]")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression '[1,2,3]' to be 'ARRAY' containing 'STRING' items,"
                + " but was 'ARRAY' containing at least one non-'STRING' item.");
  }

  @Test
  public void shouldResolveIncidentAfterCandidateGroupsExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(t -> t.zeebeCandidateGroupsExpression("MISSING_VAR")))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("MISSING_VAR", List.of("a string value", "and another"))))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ELEMENT_ID)
                .exists())
        .isTrue();

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }

  @Test
  public void shouldCreateIncidentIfCandidateUsersExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(u -> u.zeebeCandidateUsersExpression("MISSING_VAR")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'MISSING_VAR': no variable found for name 'MISSING_VAR'");
  }

  @Test
  public void shouldCreateIncidentIfCandidateUsersExpressionOfInvalidType() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithUserTask(u -> u.zeebeCandidateUsersExpression("\"not a list\"")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression '\"not a list\"' to be 'ARRAY', but was 'STRING'.");
  }

  @Test
  public void shouldCreateIncidentIfCandidateUsersExpressionOfInvalidArrayItemType() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(u -> u.zeebeCandidateUsersExpression("[1,2,3]")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression '[1,2,3]' to be 'ARRAY' containing 'STRING' items,"
                + " but was 'ARRAY' containing at least one non-'STRING' item.");
  }

  @Test
  public void shouldResolveIncidentAfterCandidateUsersExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(t -> t.zeebeCandidateUsersExpression("MISSING_VAR")))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("MISSING_VAR", List.of("a string value", "and another"))))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ELEMENT_ID)
                .exists())
        .isTrue();

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }

  // --------------------------------------------------------------------------
  // ----- TaskSchedule related tests

  @Test
  public void shouldCreateIncidentIfDueDateExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(u -> u.zeebeDueDateExpression("MISSING_VAR")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'MISSING_VAR': no variable found for name 'MISSING_VAR'");
  }

  @Test
  public void shouldCreateIncidentIfDueDateExpressionOfInvalidType() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(u -> u.zeebeDueDateExpression("[1,2,3]")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression '[1,2,3]' to be one of '[DATE_TIME, STRING]', but was 'ARRAY'");
  }

  @Test
  public void shouldResolveIncidentAndCreateNewIncidentWhenContinuationFailsOnDueDate() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(t -> t.zeebeDueDateExpression("MISSING_VAR")))
        .deploy();

    // and an instance of that process is created without a variable for the dueDate expression
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // and an incident created
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when we try to resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(incidentCreated.getKey())
                .exists())
        .describedAs("original incident is resolved")
        .isTrue();

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(i -> i.getKey() != incidentCreated.getKey())
                .exists())
        .describedAs("a new incident is created")
        .isTrue();
  }

  @Test
  public void shouldResolveIncidentAfterDueDateExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(t -> t.zeebeDueDateExpression("MISSING_VAR")))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("MISSING_VAR", "2023-02-28T10:39:23+02:00")))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ELEMENT_ID)
                .exists())
        .isTrue();

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }

  @Test
  public void shouldCreateIncidentIfFollowUpDateExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(u -> u.zeebeFollowUpDateExpression("MISSING_VAR")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'MISSING_VAR': no variable found for name 'MISSING_VAR'");
  }

  @Test
  public void shouldCreateIncidentIfFollowUpDateExpressionOfInvalidType() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(u -> u.zeebeFollowUpDateExpression("[1,2,3]")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, userTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression '[1,2,3]' to be one of '[DATE_TIME, STRING]', but was 'ARRAY'");
  }

  @Test
  public void shouldResolveIncidentAndCreateNewIncidentWhenContinuationFailsOnFollowUpDate() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(t -> t.zeebeFollowUpDateExpression("MISSING_VAR")))
        .deploy();

    // and an instance of that process is created without a variable for the followUpDate expression
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // and an incident created
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when we try to resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(incidentCreated.getKey())
                .exists())
        .describedAs("original incident is resolved")
        .isTrue();

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(i -> i.getKey() != incidentCreated.getKey())
                .exists())
        .describedAs("a new incident is created")
        .isTrue();
  }

  @Test
  public void shouldResolveIncidentAfterFollowUpDateExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithUserTask(t -> t.zeebeFollowUpDateExpression("MISSING_VAR")))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("MISSING_VAR", "2023-02-28T10:39:23+02:00")))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ELEMENT_ID)
                .exists())
        .isTrue();

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }
}

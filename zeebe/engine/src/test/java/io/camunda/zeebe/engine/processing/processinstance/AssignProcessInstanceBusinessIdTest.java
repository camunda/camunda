/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the late Business ID assignment command with Business ID uniqueness disabled (ADR 0006):
 * the happy path, the rejection matrix, and that the assignment integrates with the instance
 * lifecycle. Rejection when uniqueness is enabled is covered by {@link
 * AssignProcessInstanceBusinessIdWhenUniquenessEnabledTest}.
 */
public final class AssignProcessInstanceBusinessIdTest {

  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance USER_TASK_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldAssignBusinessIdToRunningInstance() {
    // given
    engine.deployment().withXmlResource(USER_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();

    // then
    final var assigned =
        RecordingExporter.processInstanceBusinessIdRecords(ProcessInstanceBusinessIdIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();
    assertThat(assigned.getBusinessId()).isEqualTo("biz-1");
    assertThat(assigned.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(assigned.getBpmnProcessId()).isEqualTo(PROCESS_ID);
  }

  @Test
  public void shouldRejectWhenProcessInstanceNotFound() {
    // given
    final long unknownKey = 123_456_789L;

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(unknownKey)
            .businessIdAssignment()
            .withBusinessId("biz-1")
            .expectRejection()
            .assign();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectWhenTargetIsChildProcessInstance() {
    // given
    final String childProcessId = "childProcess";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .userTask("childTask", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .deploy();
    final long parentKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long childKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(childKey)
            .businessIdAssignment()
            .withBusinessId("biz-1")
            .expectRejection()
            .assign();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectWhenProcessInstanceIsNotActive() {
    // given: a process with a process-level end execution listener that holds the root process
    // instance in the (non-active) COMPLETING state until the listener job completes
    final String endListenerType = "end-listener";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeExecutionListener(el -> el.end().type(endListenerType))
                .startEvent()
                .manualTask()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // wait until the process instance is completing (i.e. no longer active)
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETING)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .businessIdAssignment()
            .withBusinessId("biz-1")
            .expectRejection()
            .assign();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectWhenBusinessIdIsEmpty() {
    // given
    engine.deployment().withXmlResource(USER_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .businessIdAssignment()
            .withBusinessId("")
            .expectRejection()
            .assign();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectWhenBusinessIdExceedsMaxLength() {
    // given
    engine.deployment().withXmlResource(USER_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final String tooLong = "b".repeat(BusinessIdValidator.MAX_BUSINESS_ID_LENGTH + 1);

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .businessIdAssignment()
            .withBusinessId(tooLong)
            .expectRejection()
            .assign();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectWhenAlreadyAssignedWithDifferentValue() {
    // given
    engine.deployment().withXmlResource(USER_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .businessIdAssignment()
            .withBusinessId("biz-2")
            .expectRejection()
            .assign();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldAcceptIdenticalAssignmentAsNoOpWithoutSecondEvent() {
    // given
    engine.deployment().withXmlResource(USER_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();

    // when: an identical assignment (no-op) followed by a different one used as a barrier
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assignExpectingNoEvent();
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId("biz-2")
        .expectRejection()
        .assign();

    // then: only the first assignment produced an ASSIGNED event
    final var records =
        RecordingExporter.processInstanceBusinessIdRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limit(r -> r.getRecordType() == RecordType.COMMAND_REJECTION)
            .asList();
    assertThat(records)
        .filteredOn(r -> r.getIntent() == ProcessInstanceBusinessIdIntent.ASSIGNED)
        .hasSize(1);
  }

  @Test
  public void shouldCarryAssignedBusinessIdOnCompletion() {
    // given: a business id is assigned to a running instance
    engine.deployment().withXmlResource(USER_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();

    // when: the instance completes
    engine.userTask().ofInstance(processInstanceKey).complete();

    // then: the process completion carries the late-assigned business id, which drives the existing
    // uniqueness-index cleanup. The instance completing without error proves the index entry
    // inserted on assignment is removed symmetrically (ADR 0006, D7).
    final var completed =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue();
    assertThat(completed.getBusinessId()).isEqualTo("biz-1");
  }

  @Test
  public void shouldCarryAssignedBusinessIdOnTermination() {
    // given: a business id is assigned to a running instance
    engine.deployment().withXmlResource(USER_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();

    // when: the instance is canceled
    engine.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then: the process termination carries the late-assigned business id, which drives the
    // existing
    // uniqueness-index cleanup. The instance terminating without error proves the index entry
    // inserted on assignment is removed symmetrically (ADR 0006, D7).
    final var terminated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue();
    assertThat(terminated.getBusinessId()).isEqualTo("biz-1");
  }
}

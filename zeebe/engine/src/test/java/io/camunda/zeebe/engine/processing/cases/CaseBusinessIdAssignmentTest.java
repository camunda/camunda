/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue.CaseStageValue;
import io.camunda.zeebe.protocol.record.value.CaseStageState;
import io.camunda.zeebe.protocol.record.value.ImmutableCaseRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that a late business ID assignment (through the {@code ASSIGN} command or through job
 * completion) updates the stored case with a {@code CASE.BUSINESS_ID_ASSIGNED} event. Business ID
 * uniqueness is disabled, which is the engine default and a precondition for late assignments.
 */
public final class CaseBusinessIdAssignmentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String TITLE_VARIABLE = "loanTitle";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldUpdateCaseWhenBusinessIdIsAssigned() {
    // given
    final var processId = deployCaseProcess();
    final var businessId = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable(TITLE_VARIABLE, "Loan for ACME")
            .create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId(businessId)
        .assign();

    // then
    final var created =
        RecordingExporter.caseRecords(CaseIntent.CREATED)
            .valueFilter(value -> value.getProcessInstanceKey() == processInstanceKey)
            .getFirst();
    final var assigned = awaitCaseBusinessIdAssigned(processInstanceKey);

    assertThat(assigned.getKey()).isEqualTo(created.getValue().getCaseKey());
    assertThat(assigned.getValue().getBusinessId()).isEqualTo(businessId);
    assertThat(
            ImmutableCaseRecordValue.copyOf(assigned.getValue())
                .withBusinessId(created.getValue().getBusinessId()))
        .isEqualTo(ImmutableCaseRecordValue.copyOf(created.getValue()));
    assertThat(assigned.getValue().getStages())
        .extracting(CaseStageValue::getElementId, CaseStageValue::getName, CaseStageValue::getState)
        .containsExactly(tuple("stage-a", "Stage A", CaseStageState.NOT_STARTED));
  }

  @Test
  public void shouldUpdateCaseWhenJobCompletionAssignsBusinessId() {
    // given
    final var processId = deployCaseProcess();
    final var businessId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(processId)
        .withBusinessId(businessId)
        .complete();

    // then
    final var assigned = awaitCaseBusinessIdAssigned(processInstanceKey);
    final var created =
        RecordingExporter.caseRecords(CaseIntent.CREATED)
            .valueFilter(value -> value.getProcessInstanceKey() == processInstanceKey)
            .getFirst();
    assertThat(assigned.getKey()).isEqualTo(created.getValue().getCaseKey());
    assertThat(assigned.getValue().getCaseKey()).isEqualTo(created.getValue().getCaseKey());
    assertThat(assigned.getKey()).isNotEqualTo(processInstanceKey);
    assertThat(assigned.getValue().getBusinessId()).isEqualTo(businessId);
    assertThat(assigned.getValue().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  public void shouldNotWriteCaseRecordForInstanceWithoutCase() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", task -> task.zeebeJobType(processId))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId(Strings.newRandomValidBpmnId())
        .assign();
    // completing the process bounds the stream, so that a late case record would be seen
    ENGINE.job().ofInstance(processInstanceKey).withType(processId).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceBusinessIdRecords(
                    ProcessInstanceBusinessIdIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .withValueType(ValueType.CASE)
                .asList())
        .isEmpty();
  }

  @Test
  public void shouldNotWriteCaseRecordWhenAssignmentIsRejected() {
    // given
    final var processId = deployCaseProcess();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withBusinessId(Strings.newRandomValidBpmnId())
            .create();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .businessIdAssignment()
            .withBusinessId(Strings.newRandomValidBpmnId())
            .expectRejection()
            .assign();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(
            RecordingExporter.records()
                .limit(record -> record.getPosition() == rejection.getPosition())
                .withValueType(ValueType.CASE)
                .asList())
        .extracting(Record::getIntent)
        .containsExactly(CaseIntent.CREATED);
  }

  /**
   * Deploys a case-marked process with the stage {@code stage-a} that waits at a service task (job
   * type = process id) after the stage.
   */
  private static String deployCaseProcess() {
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeCase(TITLE_VARIABLE)
                .startEvent()
                .subProcess(
                    "stage-a",
                    stage ->
                        stage
                            .name("Stage A")
                            .zeebeStage()
                            .embeddedSubProcess()
                            .startEvent()
                            .endEvent())
                .serviceTask("task", task -> task.zeebeJobType(processId))
                .endEvent()
                .done())
        .deploy();
    return processId;
  }

  private static Record<CaseRecordValue> awaitCaseBusinessIdAssigned(
      final long processInstanceKey) {
    return RecordingExporter.caseRecords(CaseIntent.BUSINESS_ID_ASSIGNED)
        .valueFilter(value -> value.getProcessInstanceKey() == processInstanceKey)
        .getFirst();
  }
}

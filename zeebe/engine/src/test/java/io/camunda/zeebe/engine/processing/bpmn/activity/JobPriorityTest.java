/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobPriorityTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withInitialClusterVersionAtMax();

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static Record<JobRecordValue> awaitJobCreated(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  @Test
  public void shouldDefaultPriorityToZeroWhenNoDefinition() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(0);
  }

  @Test
  public void shouldUseTaskLevelLiteralPriorityOnServiceTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriority("42"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(42);
  }

  @Test
  public void shouldUseTaskLevelFeelPriorityOnServiceTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriorityExpression("tier"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("tier", 75).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(75);
  }

  @Test
  public void shouldUseProcessLevelDefaultWhenTaskLevelAbsent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriority("33")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(33);
  }

  @Test
  public void shouldUseProcessLevelFeelExpressionWhenTaskLevelAbsent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriorityExpression("tier")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("tier", 55).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(55);
  }

  @Test
  public void shouldAcceptDecimalScaleIntegerAsPriority() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriorityExpression("p"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("p", 1.0).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(1);
  }

  @Test
  public void shouldPreferTaskLevelOverProcessLevel() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriority("10")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriority("99"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(99);
  }

  @Test
  public void shouldOverrideProcessLevelWithTaskLevelLiteralZero() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriority("33")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriority("0"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(0);
  }

  @Test
  public void shouldPopulatePriorityOnSendTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .sendTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriority("60"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(60);
  }

  @Test
  public void shouldPopulatePriorityOnScriptTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .scriptTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriority("77"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(77);
  }

  @Test
  public void shouldPopulatePriorityOnBusinessRuleTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .businessRuleTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriority("88"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(88);
  }

  @Test
  public void shouldRaiseIncidentWhenFeelExpressionReferencesMissingVariable() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriorityExpression("missingVar"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasElementId("task")
        .hasJobKey(-1L);
    assertThat(incident.getValue().getErrorMessage()).contains("missingVar");
  }

  @Test
  public void shouldRaiseIncidentWhenFeelExpressionEvaluatesToNonNumber() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriorityExpression("flag"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("flag", true).create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasElementId("task")
        .hasJobKey(-1L);
    assertThat(incident.getValue().getErrorMessage())
        .contains("for the job priority to be 'NUMBER'");
  }

  @Test
  public void shouldRaiseIncidentWhenFeelExpressionEvaluatesToFraction() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriorityExpression("p"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("p", 1.5).create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasElementId("task")
        .hasJobKey(-1L);
    assertThat(incident.getValue().getErrorMessage())
        .contains("for the job priority to be an integer within the 32-bit signed range");
  }

  @Test
  public void shouldRaiseIncidentWhenFeelExpressionExceedsIntegerMax() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriorityExpression("p"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("p", Integer.MAX_VALUE + 1L)
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasElementId("task")
        .hasJobKey(-1L);
    assertThat(incident.getValue().getErrorMessage())
        .contains("for the job priority to be an integer within the 32-bit signed range");
  }

  @Test
  public void shouldRaiseIncidentWhenFeelExpressionBelowIntegerMin() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriorityExpression("p"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("p", Integer.MIN_VALUE - 1L)
            .create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasElementId("task")
        .hasJobKey(-1L);
    assertThat(incident.getValue().getErrorMessage())
        .contains("for the job priority to be an integer within the 32-bit signed range");
  }

  @Test
  public void shouldRaiseIncidentWhenProcessLevelFeelReferencesMissingVariable() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriorityExpression("missingVar")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasElementId("task")
        .hasJobKey(-1L);
    assertThat(incident.getValue().getErrorMessage()).contains("missingVar");
  }

  @Test
  public void shouldFallBackToProcessLevelPriorityOnScriptTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriority("44")
            .startEvent()
            .scriptTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(44);
  }

  @Test
  public void shouldFallBackToProcessLevelPriorityOnBusinessRuleTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriority("66")
            .startEvent()
            .businessRuleTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(awaitJobCreated(processInstanceKey).getValue()).hasPriority(66);
  }

  @Test
  public void shouldRaiseIncidentForInvalidFeelOnScriptTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .scriptTask(
                "task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriorityExpression("missingVar"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasElementId("task")
        .hasJobKey(-1L);

    assertThat(incident.getValue().getErrorMessage()).contains("missingVar");
  }

  @Test
  public void shouldNotCreateJobForDirectExecutionScriptTaskWithProcessLevelPriority() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriority("99")
            .startEvent()
            .scriptTask("task", t -> t.zeebeExpression("= 1").zeebeResultVariable("result"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withIntent(JobIntent.CREATED)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldHandleMixedDirectExecutionAndJobWorkerTasks() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriority("77")
            .startEvent()
            .scriptTask("scriptDirect", t -> t.zeebeExpression("= 1").zeebeResultVariable("result"))
            .serviceTask("svc", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> serviceJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("svc")
            .getFirst();
    Assertions.assertThat(serviceJob.getValue()).hasPriority(77);
  }

  @Test
  public void shouldUseDefaultPriorityForTaskListenerJob() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriority("77")
            .startEvent()
            .userTask(
                "task",
                t ->
                    t.zeebeUserTask()
                        .zeebeTaskListener(l -> l.creating().type("creating-listener")))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> listenerJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withJobKind(JobKind.TASK_LISTENER)
            .getFirst();
    Assertions.assertThat(listenerJob.getValue()).hasPriority(0);
  }

  @Test
  public void shouldUseDefaultPriorityForExecutionListenerJob() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeJobPriority("77")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeExecutionListener(el -> el.start().type("start-listener")))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> listenerJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .getFirst();
    Assertions.assertThat(listenerJob.getValue()).hasPriority(0);
  }

  @Test
  public void shouldRaiseIncidentForInvalidFeelOnBusinessRuleTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .businessRuleTask(
                "task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriorityExpression("missingVar"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasElementId("task")
        .hasJobKey(-1L);

    assertThat(incident.getValue().getErrorMessage()).contains("missingVar");
  }
}

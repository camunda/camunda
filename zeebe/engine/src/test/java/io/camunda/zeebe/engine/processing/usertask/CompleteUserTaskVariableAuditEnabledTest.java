/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CompleteUserTaskVariableAuditEnabledTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setUserTaskCompletionVariableAuditEnabled(true));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldMarkCreatedVariableAsComingFromUserTaskCompletion() {
    // given
    final long processInstanceKey = createInstance(userTaskProcess());

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).withVariable("foo", "bar").complete();

    // then
    assertSource(
        processInstanceKey,
        "foo",
        VariableIntent.CREATED,
        VariableOperationType.USER_TASK_COMPLETION);
  }

  @Test
  public void shouldMarkUpdatedVariableAsComingFromUserTaskCompletion() {
    // given
    ENGINE.deployment().withXmlResource(userTaskProcess()).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withVariable("foo", "old").create();

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).withVariable("foo", "new").complete();

    // then
    assertSource(
        processInstanceKey,
        "foo",
        VariableIntent.UPDATED,
        VariableOperationType.USER_TASK_COMPLETION);
  }

  @Test
  public void shouldNotWriteVariableRecordForUnchangedValue() {
    // given
    ENGINE.deployment().withXmlResource(userTaskProcess()).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withVariable("foo", "same").create();
    final long initialPosition =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("foo")
            .getFirst()
            .getPosition();

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).withVariable("foo", "same").complete();

    // then
    RecordingExporter.disableAwaitingIncomingRecords();
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withName("foo")
                .filter(record -> record.getPosition() > initialPosition)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldOnlyMarkOutputMappingResultAsComingFromUserTaskCompletion() {
    // given
    final long processInstanceKey =
        createInstance(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .userTask("task", task -> task.zeebeOutputExpression("input", "result"))
                .zeebeUserTask()
                .endEvent()
                .done());

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withVariables("{'input':'mapped','discarded':'temporary'}")
        .complete();

    // then
    final List<Record<VariableRecordValue>> completionVariables =
        RecordingExporter.variableRecords()
            .withProcessInstanceKey(processInstanceKey)
            .filter(
                record ->
                    List.of("input", "discarded", "result").contains(record.getValue().getName()))
            .limit(3)
            .asList();
    assertThat(completionVariables)
        .filteredOn(record -> "result".equals(record.getValue().getName()))
        .singleElement()
        .extracting(record -> record.getValue().getSource().getType())
        .isEqualTo(VariableOperationType.USER_TASK_COMPLETION);
    assertThat(completionVariables)
        .filteredOn(record -> !"result".equals(record.getValue().getName()))
        .extracting(record -> record.getValue().getSource().getType())
        .containsOnly(VariableOperationType.UNKNOWN);
  }

  private static long createInstance(final BpmnModelInstance process) {
    ENGINE.deployment().withXmlResource(process).deploy();
    return ENGINE.processInstance().ofBpmnProcessId("process").create();
  }

  private static BpmnModelInstance userTaskProcess() {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .userTask("task")
        .zeebeUserTask()
        .endEvent()
        .done();
  }

  private static void assertSource(
      final long processInstanceKey,
      final String variableName,
      final VariableIntent intent,
      final VariableOperationType source) {
    assertThat(
            RecordingExporter.variableRecords(intent)
                .withProcessInstanceKey(processInstanceKey)
                .withName(variableName)
                .getFirst()
                .getValue()
                .getSource()
                .getType())
        .isEqualTo(source);
  }
}

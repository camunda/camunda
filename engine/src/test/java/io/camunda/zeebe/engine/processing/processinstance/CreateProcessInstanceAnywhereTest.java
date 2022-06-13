/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CreateProcessInstanceAnywhereTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldActivateSingleElement() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent("end").done())
        .deploy();

    // When
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withStartInstruction(newStartInstruction("end"))
            .create();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType(), Record::getIntent)
        .describedAs("Expected to start process instance at end event")
        .containsSequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT))
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldActivateMultipleElements() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("start")
                .parallelGateway("forking")
                .manualTask("task1")
                .parallelGateway("joining")
                .moveToNode("forking")
                .manualTask("task2")
                .connectTo("joining")
                .endEvent()
                .done())
        .deploy();

    // When
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withStartInstruction(newStartInstruction("task1"))
            .withStartInstruction(newStartInstruction("task2"))
            .create();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            record -> record.getValue().getElementId(),
            record -> record.getValue().getBpmnElementType(),
            Record::getIntent)
        .describedAs("Expected to start process instance at both tasks")
        .containsSequence(
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task1", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("task2", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ACTIVATE_ELEMENT))
        .containsSubsequence(
            tuple("task1", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task2", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task1", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("task2", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple("start", BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("start", BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "forking",
                BpmnElementType.PARALLEL_GATEWAY,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "forking",
                BpmnElementType.PARALLEL_GATEWAY,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldActivateElementWithinSubprocess() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("start_root")
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent("start_lvl_1")
                            .manualTask("task")
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    // When
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withStartInstruction(newStartInstruction("task"))
            .create();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            record -> record.getValue().getElementId(),
            record -> record.getValue().getBpmnElementType(),
            Record::getIntent)
        .describedAs("Expected to start process instance at task inside subprocess")
        .containsSequence(
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "subprocess",
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                "subprocess", BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ACTIVATE_ELEMENT))
        .containsSubsequence(
            tuple("task", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "subprocess", BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple(
                "start_root", BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "start_lvl_1",
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldActivateMultipleElementsWithinSubprocess() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("start_root")
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent("start_lvl_1")
                            .parallelGateway("forking")
                            .manualTask("task1")
                            .parallelGateway("joining")
                            .moveToNode("forking")
                            .manualTask("task2")
                            .connectTo("joining")
                            .endEvent()
                            .done())
                .endEvent()
                .done())
        .deploy();

    // When
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withStartInstruction(newStartInstruction("task1"))
            .withStartInstruction(newStartInstruction("task2"))
            .create();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            record -> record.getValue().getElementId(),
            record -> record.getValue().getBpmnElementType(),
            Record::getIntent)
        .describedAs("Expected to start process instance at both tasks inside subprocess")
        .containsSequence(
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "subprocess",
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                "subprocess", BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task1", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("task2", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ACTIVATE_ELEMENT))
        .containsSubsequence(
            tuple("task1", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task2", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task1", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("task2", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "subprocess", BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple(
                "start_root", BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "start_lvl_1",
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "forking",
                BpmnElementType.PARALLEL_GATEWAY,
                ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldActivateElementWithinNestedSubprocess() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("start_root")
                .subProcess(
                    "subprocess_lvl_1",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent("start_lvl_1")
                            .subProcess(
                                "subprocess_lvl_2",
                                ns ->
                                    ns.embeddedSubProcess()
                                        .startEvent("start_lvl_2")
                                        .manualTask("task")
                                        .endEvent())
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    // When
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withStartInstruction(newStartInstruction("task"))
            .create();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            record -> record.getValue().getElementId(),
            record -> record.getValue().getBpmnElementType(),
            Record::getIntent)
        .describedAs("Expected to start process instance at task inside nested subprocess")
        .containsSequence(
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "subprocess_lvl_1",
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                "subprocess_lvl_1",
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "subprocess_lvl_2",
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                "subprocess_lvl_2",
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ACTIVATE_ELEMENT))
        .containsSubsequence(
            tuple("task", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task", BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "subprocess_lvl_2",
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "subprocess_lvl_1",
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple(
                "start_root", BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "start_lvl_1",
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "start_lvl_2",
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCreateVariablesInProcessScope() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent("end").done())
        .deploy();

    // When
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withStartInstruction(newStartInstruction("end"))
            .withVariable("variable", 123)
            .create();

    // Then
    Assertions.assertThat(
            RecordingExporter.records()
                .limit(
                    // extract the condition into a method like limitToProcessInstance()
                    r ->
                        r.getKey() == processInstanceKey
                            && r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED)
                .variableRecords())
        .hasSize(1)
        .extracting(Record::getValue)
        .extracting(
            VariableRecordValue::getScopeKey,
            VariableRecordValue::getName,
            VariableRecordValue::getValue)
        .describedAs("Expected the variable to be created in the scope of the process instance")
        .containsExactly(tuple(processInstanceKey, "variable", "123"));
  }

  private ProcessInstanceCreationStartInstruction newStartInstruction(final String elementId) {
    return new ProcessInstanceCreationStartInstruction().setElementId(elementId);
  }
}

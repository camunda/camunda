/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CreateProcessInstanceAnywhereTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldActivateSingleElement() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process").startEvent().endEvent("end").done())
        .deploy();

    // When
    final long key =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withStartInstruction(newStartInstruction("end"))
            .create();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType())
        .doesNotContain(BpmnElementType.START_EVENT);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            Tuple.tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldActivateMultipleElements() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .parallelGateway("gateway")
                .serviceTask("task1", t -> t.zeebeJobType("type1"))
                .parallelGateway("joining")
                .moveToNode("gateway")
                .serviceTask("task2", t -> t.zeebeJobType("type2"))
                .connectTo("joining")
                .endEvent()
                .done())
        .deploy();

    // When
    final long key =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withStartInstruction(newStartInstruction("task1"))
            .withStartInstruction(newStartInstruction("task2"))
            .create();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(key)
                .limit(2)
                .count())
        .isEqualTo(2);
    ENGINE.job().ofInstance(key).withType("type1").complete();
    ENGINE.job().ofInstance(key).withType("type2").complete();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType())
        .doesNotContain(BpmnElementType.START_EVENT);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .limitToProcessInstanceCompleted())
        .extracting(
            record -> record.getValue().getElementId(),
            record -> record.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            Tuple.tuple(
                "process", BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(
                "process", BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(
                "task1", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(
                "task1", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(
                "task2", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(
                "task2", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldActivateElementWithinSubprocess() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("task", t -> t.zeebeJobType("type"))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    // When
    final long key =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withStartInstruction(newStartInstruction("task"))
            .create();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(key)
                .limit(1)
                .count())
        .isEqualTo(1);
    ENGINE.job().ofInstance(key).withType("type").complete();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType())
        .doesNotContain(BpmnElementType.START_EVENT);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            Tuple.tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldActivateMultipleElementWithinSubprocess() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .parallelGateway("gateway")
                            .serviceTask("task1", t -> t.zeebeJobType("type1"))
                            .parallelGateway("joining")
                            .moveToNode("gateway")
                            .serviceTask("task2", t -> t.zeebeJobType("type2"))
                            .connectTo("joining")
                            .endEvent()
                            .done())
                .endEvent()
                .done())
        .deploy();

    // When
    final long key =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withStartInstruction(newStartInstruction("task1"))
            .withStartInstruction(newStartInstruction("task2"))
            .create();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(key)
                .limit(2)
                .count())
        .isEqualTo(2);
    ENGINE.job().ofInstance(key).withType("type1").complete();
    ENGINE.job().ofInstance(key).withType("type2").complete();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType())
        .doesNotContain(BpmnElementType.START_EVENT);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .limitToProcessInstanceCompleted())
        .extracting(
            record -> record.getValue().getElementId(),
            record -> record.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            Tuple.tuple(
                "process", BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(
                "process", BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(
                "subprocess",
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(
                "subprocess", BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(
                "task1", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(
                "task1", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(
                "task2", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(
                "task2", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldActivateElementWithinNestedSubprocess() {
    // Given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .subProcess(
                                "nestedSubprocess",
                                ns ->
                                    ns.embeddedSubProcess()
                                        .startEvent()
                                        .serviceTask("task", t -> t.zeebeJobType("type"))
                                        .endEvent())
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    // When
    final long key =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withStartInstruction(newStartInstruction("task"))
            .create();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(key)
                .limit(1)
                .count())
        .isEqualTo(1);
    ENGINE.job().ofInstance(key).withType("type").complete();

    // Then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType())
        .doesNotContain(BpmnElementType.START_EVENT);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(key)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            Tuple.tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Tuple.tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            Tuple.tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  private ProcessInstanceCreationStartInstruction newStartInstruction(final String elementId) {
    return new ProcessInstanceCreationStartInstruction().setElementId(elementId);
  }
}

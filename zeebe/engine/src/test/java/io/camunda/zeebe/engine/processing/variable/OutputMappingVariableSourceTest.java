/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Checks the source of the variables that an output mapping writes. The source names the operation,
 * and the element whose output mapping wrote the variable. The payload that the element received
 * keeps its own source.
 */
public final class OutputMappingVariableSourceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();
  private final String messageName = Strings.newRandomValidBpmnId();

  @Test
  public void shouldSourceMappedVariableFromOutputMappingAndKeepPayloadSource() {
    // given
    final long processInstanceKey =
        createInstance(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task", t -> t.zeebeJobType(jobType).zeebeOutputExpression("x", "total"))
                .endEvent()
                .done());
    final long taskKey = elementInstanceKey(processInstanceKey, "task");

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).withVariable("x", 1).complete();

    // then
    assertThat(variableRecords(processInstanceKey))
        .extracting(
            record -> record.getValue().getName(),
            record -> record.getValue().getScopeKey(),
            OutputMappingVariableSourceTest::sourceOf)
        .containsExactlyInAnyOrder(
            tuple("x", taskKey, tuple(VariableOperationType.JOB_COMPLETION, taskKey, "task")),
            tuple(
                "total",
                processInstanceKey,
                tuple(VariableOperationType.OUTPUT_MAPPING, taskKey, "task")));
  }

  @Test
  public void shouldSourceUpdatedVariableFromOutputMapping() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task", t -> t.zeebeJobType(jobType).zeebeOutputExpression("x", "total"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("total", 0).create();
    final long taskKey = elementInstanceKey(processInstanceKey, "task");

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).withVariable("x", 1).complete();

    // then
    assertThat(variableRecords(processInstanceKey, "total"))
        .filteredOn(record -> record.getIntent() == VariableIntent.UPDATED)
        .extracting(OutputMappingVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.OUTPUT_MAPPING, taskKey, "task"));
  }

  @Test
  public void shouldSourceMappedVariableOfSubProcessFromSubProcess() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "sub",
                    s ->
                        s.zeebeInputExpression("=1", "local")
                            .zeebeOutputExpression("local", "total")
                            .embeddedSubProcess()
                            .startEvent()
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final long subKey = elementInstanceKey(processInstanceKey, "sub");
    assertThat(variableRecords(processInstanceKey, "total"))
        .extracting(OutputMappingVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.OUTPUT_MAPPING, subKey, "sub"));
  }

  @Test
  public void shouldSourceMappedVariableOfAdHocSubProcessAndNotItsOutputCollection() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .adHocSubProcess(
                    "ahsp",
                    a -> {
                      a.zeebeActiveElementsCollectionExpression("activateElements")
                          .zeebeOutputCollection("results")
                          .zeebeOutputElementExpression("result")
                          .zeebeOutputExpression("results", "total");
                      a.serviceTask("task", t -> t.zeebeJobType(jobType));
                    })
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("activateElements", List.of("task"))
            .create();
    final long ahspKey = elementInstanceKey(processInstanceKey, "ahsp");

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(jobType)
        .withVariable("result", 1)
        .complete();

    // then
    assertThat(variableRecords(processInstanceKey, "total"))
        .extracting(OutputMappingVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.OUTPUT_MAPPING, ahspKey, "ahsp"));
    assertThat(variableRecords(processInstanceKey, "results"))
        .isNotEmpty()
        .extracting(record -> record.getValue().getSource().getType())
        .doesNotContain(VariableOperationType.OUTPUT_MAPPING);
  }

  @Test
  public void shouldSourceMappedVariableOfMultiInstanceFromInnerInstance() {
    // given
    final long processInstanceKey =
        createInstance(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(jobType)
                            .zeebeOutputExpression("result", "total")
                            .multiInstance(
                                m ->
                                    m.sequential()
                                        .zeebeInputCollectionExpression("[1,2]")
                                        .zeebeInputElement("item")))
                .endEvent()
                .done());

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(jobType)
        .withVariable("result", 10)
        .complete();
    final var secondJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(jobType)
            .skip(1)
            .getFirst();
    ENGINE.job().withKey(secondJob.getKey()).withVariable("result", 20).complete();

    // then
    final var innerInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .limit(2)
            .map(Record::getKey)
            .toList();
    assertThat(variableRecords(processInstanceKey, "total"))
        .extracting(
            record -> record.getValue().getValue(), OutputMappingVariableSourceTest::sourceOf)
        .containsExactly(
            tuple(
                "10",
                tuple(VariableOperationType.OUTPUT_MAPPING, innerInstanceKeys.get(0), "task")),
            tuple(
                "20",
                tuple(VariableOperationType.OUTPUT_MAPPING, innerInstanceKeys.get(1), "task")));
  }

  @Test
  public void shouldSourceMappedVariableOfMessageCatchEventFromCatchEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "catch",
                    e ->
                        e.message(m -> m.name(messageName).zeebeCorrelationKey("=key"))
                            .zeebeOutputExpression("x", "total"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "k1").create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    final long catchKey = elementInstanceKey(processInstanceKey, "catch");

    // when
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey("k1")
        .withVariables(Map.of("x", 1))
        .publish();

    // then
    assertThat(variableRecords(processInstanceKey))
        .filteredOn(record -> !record.getValue().getName().equals("key"))
        .extracting(
            record -> record.getValue().getName(), OutputMappingVariableSourceTest::sourceOf)
        .containsExactlyInAnyOrder(
            tuple("x", tuple(VariableOperationType.UNKNOWN, catchKey, "catch")),
            tuple("total", tuple(VariableOperationType.OUTPUT_MAPPING, catchKey, "catch")));
  }

  @Test
  public void shouldSourceMappedVariableOfBoundaryEventFromBoundaryEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .boundaryEvent(
                    "boundary",
                    b ->
                        b.message(m -> m.name(messageName).zeebeCorrelationKey("=key"))
                            .zeebeOutputExpression("y", "total"))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "k1").create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey("k1")
        .withVariables(Map.of("y", 1))
        .publish();

    // then
    final long boundaryKey = elementInstanceKey(processInstanceKey, "boundary");
    assertThat(variableRecords(processInstanceKey, "total"))
        .extracting(OutputMappingVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.OUTPUT_MAPPING, boundaryKey, "boundary"));
  }

  @Test
  public void shouldSourceMappedVariableOfCallActivityFromCallActivity() {
    // given
    final var childProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .callActivity(
                    "call",
                    c ->
                        c.zeebeProcessId(childProcessId)
                            .zeebePropagateAllChildVariables(false)
                            .zeebeOutputExpression("z", "total"))
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("childTask", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long callKey = elementInstanceKey(processInstanceKey, "call");
    final var childJob =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).getFirst();

    // when
    ENGINE.job().withKey(childJob.getKey()).withVariable("z", 1).complete();

    // then
    assertThat(variableRecords(processInstanceKey))
        .filteredOn(record -> record.getValue().getProcessInstanceKey() == processInstanceKey)
        .extracting(
            record -> record.getValue().getName(),
            record -> record.getValue().getScopeKey(),
            OutputMappingVariableSourceTest::sourceOf)
        .containsExactlyInAnyOrder(
            tuple("z", callKey, tuple(VariableOperationType.UNKNOWN, callKey, "call")),
            tuple(
                "total",
                processInstanceKey,
                tuple(VariableOperationType.OUTPUT_MAPPING, callKey, "call")));
  }

  @Test
  public void shouldSourceMappedVariableOfJobBasedUserTaskFromUserTask() {
    // given
    final long processInstanceKey =
        createInstance(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", t -> t.zeebeOutputExpression("x", "total"))
                .endEvent()
                .done());
    final long taskKey = elementInstanceKey(processInstanceKey, "task");

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(Protocol.USER_TASK_JOB_TYPE)
        .withVariable("x", 1)
        .complete();

    // then
    assertThat(variableRecords(processInstanceKey, "total"))
        .extracting(OutputMappingVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.OUTPUT_MAPPING, taskKey, "task"));
  }

  private long createInstance(final BpmnModelInstance process) {
    ENGINE.deployment().withXmlResource(process).deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }

  private static long elementInstanceKey(final long processInstanceKey, final String elementId) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(elementId)
        .getFirst()
        .getKey();
  }

  /** Returns the variable records until the process instance is completed. */
  private static List<Record<VariableRecordValue>> variableRecords(final long processInstanceKey) {
    return RecordingExporter.records()
        .betweenProcessInstance(processInstanceKey)
        .variableRecords()
        .toList();
  }

  private static List<Record<VariableRecordValue>> variableRecords(
      final long processInstanceKey, final String name) {
    return variableRecords(processInstanceKey).stream()
        .filter(record -> record.getValue().getName().equals(name))
        .toList();
  }

  private static Tuple sourceOf(final Record<VariableRecordValue> record) {
    final var source = record.getValue().getSource();
    return tuple(source.getType(), source.getElementInstanceKey(), source.getElementId());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CoolExpressionContextTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldAccessTheElement() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .name("Start")
            .zeebeOutputExpression("camunda.process.elements[type=\"START_EVENT\"]", "elements")
            .sequenceFlowId("start-to-end")
            .endEvent("end")
            .name("End")
            .done();

    final StartEvent startEvent = process.getModelElementById("start");
    startEvent.builder().documentation("A start event.");

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // when
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .filterRootScope()
                .limitToProcessInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_COMPLETED);

    // then
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withName("elements")
                .limit(1))
        .extracting(r -> r.getValue().getValue())
        .contains(
            "[{\"documentation\":\"A start event.\",\"name\":\"Start\",\"id\":\"start\",\"type\":\"START_EVENT\"}]");
  }

  @Test
  public void shouldAccessAdHocActivities() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent("start")
                .name("Start")
                .zeebeOutputExpression(
                    "camunda.process.elements[type=\"AD_HOC_SUB_PROCESS\"][1].adHocActivities.id",
                    "adHocActivities")
                .adHocSubProcess(
                    "ad-hoc",
                    adHocSubProcess -> {
                      adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task_A\"]");
                      adHocSubProcess.task("task_A").name("A");
                      adHocSubProcess.task("task_B").name("B");
                      adHocSubProcess.task("task_C").name("C");
                    })
                .endEvent("end")
                .name("End")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // when
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .filterRootScope()
                .limitToProcessInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_COMPLETED);

    // then
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withName("adHocActivities")
                .limit(1))
        .extracting(r -> r.getValue().getValue())
        .contains("[\"task_B\",\"task_C\",\"task_A\"]");
  }
}

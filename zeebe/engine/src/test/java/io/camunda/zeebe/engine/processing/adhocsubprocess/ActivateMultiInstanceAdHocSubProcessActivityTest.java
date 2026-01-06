/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ActivateMultiInstanceAdHocSubProcessActivityTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldActivateElementInMultiInstanceAdHocSubprocess() {
    // given- a process with a multi-instance ad-hoc subprocess
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .adHocSubProcess(
                    "ad-hoc",
                    adHocSubProcess -> {
                      adHocSubProcess.multiInstance(
                          mi ->
                              mi.zeebeInputCollectionExpression("[1,2,3]")
                                  .zeebeInputElement("item"));
                      adHocSubProcess.task("A");
                      adHocSubProcess.task("B");
                    })
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // when - activate an instance of element A in each of the three ad-hoc subprocesses
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .limit(3)
        .map(Record::getKey)
        .forEach(
            adHocSubProcessInstanceKey ->
                ENGINE
                    .adHocSubProcessActivity()
                    .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
                    .withElementIds("A")
                    .activate());

    // then - see that three inner instances have been activated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("ad-hoc#innerInstance", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("ad-hoc#innerInstance", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("ad-hoc#innerInstance", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }
}

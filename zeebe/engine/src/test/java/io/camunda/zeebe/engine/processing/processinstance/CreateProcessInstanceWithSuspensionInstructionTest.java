/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CreateProcessInstanceWithSuspensionInstructionTest {

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule
  public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldSuspendProcessInstanceWhenElementIsCompleted() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .manualTask(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // then
    var result =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .limit(processId, ProcessInstanceIntent.ELEMENT_SUSPENDED)
            .filter(
                record ->
                    record.getValue().getElementId().equals(processId)
                        || record.getValue().getElementId().equals(elementToSuspend));

    assertThat(result)
        .extracting(Record::getIntent, record -> record.getValue().getElementId())
        .containsSequence(
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, elementToSuspend),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_SUSPENDED, processId));

    var processInstanceRecord = RecordingExporter.processInstanceRecords(
            ProcessInstanceIntent.ELEMENT_SUSPENDED).withProcessInstanceKey(processInstanceKey)
        .getFirst();

    ENGINE.writeRecords(RecordToWrite.command().key(processInstanceKey)
        .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, processInstanceRecord.getValue()));

    var rejectedCommandRecord = RecordingExporter.processInstanceRecords(ProcessInstanceIntent.COMPLETE_ELEMENT).withProcessInstanceKey(processInstanceKey).onlyCommandRejections().getFirst();

    assertThat(rejectedCommandRecord).isNotNull();
    assert false;
  }

  /**
   * TODO
   * shouldCancelJobWhenProcessInstanceIsSuspended
   * unsubscribeFromEvents
   * resolve incidents
   *
   */

  @Test
  public void shouldCancelJobWhenProcessInstanceIsSuspended() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeJobType("jobType"))
                .endEvent()
                .moveToLastGateway()
                .manualTask(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // then
    var result = RecordingExporter.jobRecords(JobIntent.CANCELED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

    assertThat(result.getValue().getElementId()).isEqualTo("task");
  }


}

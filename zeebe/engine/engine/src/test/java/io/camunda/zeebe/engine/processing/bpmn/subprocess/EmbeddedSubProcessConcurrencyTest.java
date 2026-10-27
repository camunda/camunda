/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.EmbeddedSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EmbeddedSubProcessConcurrencyTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          // Disable batch processing. Interrupting behaviour is only reproducible if
          // process instance is not completed in one batch.
          .maxCommandsInBatch(1);

  private static final String PROCESS_ID = "process-with-sub-process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldNotTriggerBoundaryEventWhenFlowscopeIsInterrupted() {
    // given
    final Consumer<EmbeddedSubProcessBuilder> subProcessBuilder =
        subprocess ->
            subprocess
                .startEvent()
                .parallelGateway()
                .serviceTask("task", b -> b.zeebeJobType("task"))
                .moveToLastGateway()
                .serviceTask("task2", b -> b.zeebeJobType("task2"))
                .endEvent()
                .moveToActivity("subProcess")
                .boundaryEvent(
                    "errorBoundary",
                    boundary -> boundary.cancelActivity(true).error("boundaryError"))
                .endEvent()
                .done();

    final Consumer<EventSubProcessBuilder> eventSubProcessBuilder =
        eventSubProcess ->
            eventSubProcess.startEvent("eventSubProcessStartEvent").error("espError").endEvent();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess("eventSubProcess", eventSubProcessBuilder)
                .startEvent()
                .subProcess(
                    "subProcess",
                    subProcess -> subProcessBuilder.accept(subProcess.embeddedSubProcess()))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var jobs =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .toList();
    final var firstJob = jobs.get(0);
    final var secondJob = jobs.get(1);

    // when
    // We need to make sure no records are written in between throw error commands. This could
    // cause the test to become flaky.
    final var boundaryJobRecord = new JobRecord();
    boundaryJobRecord.wrapWithoutVariables((JobRecord) firstJob.getValue());
    boundaryJobRecord.setErrorCode(BufferUtil.wrapString("boundaryError"));
    final var espJobRecord = new JobRecord();
    espJobRecord.wrapWithoutVariables((JobRecord) secondJob.getValue());
    espJobRecord.setErrorCode(BufferUtil.wrapString("espError"));
    ENGINE.writeRecords(
        // First we trigger the boundary event
        RecordToWrite.command()
            .key(firstJob.getKey())
            .job(JobIntent.THROW_ERROR, boundaryJobRecord),
        // Next we trigger the event sub process. This will interrupt the flow scope of the sub
        // process, whilst the subprocess is being terminated because of the boundary event
        RecordToWrite.command().key(secondJob.getKey()).job(JobIntent.THROW_ERROR, espJobRecord));

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .filter(r -> r.getValueType() == ValueType.PROCESS_EVENT))
        .extracting(
            Record::getIntent, r -> ((ProcessEventRecordValue) r.getValue()).getTargetElementId())
        .containsExactly(
            tuple(ProcessEventIntent.TRIGGERING, "errorBoundary"),
            tuple(ProcessEventIntent.TRIGGERING, "eventSubProcessStartEvent"),
            tuple(ProcessEventIntent.TRIGGERED, "eventSubProcessStartEvent"));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .contains(
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CompleteJobFlowScopeTest {

  // without batching, each command is processed on its own, so the order of the written commands
  // decides which state the job completion sees
  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().maxCommandsInBatch(1);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldNotCompleteElementWhenFlowScopeIsTerminating() {
    // given - the flow scope of the task is the process instance
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(Strings.newRandomValidBpmnId()))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final Record<ProcessInstanceRecordValue> processActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when - the process instance starts terminating before the job completion is processed, so
    // the job still exists but its flow scope is no longer active (the reverse order, where the
    // guard rejects the element completion, is covered by ProcessInstanceCommandRejectionTest)
    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.TERMINATE_ELEMENT, processActivated.getValue())
            .key(processActivated.getKey()),
        RecordToWrite.command()
            .job(JobIntent.COMPLETE, jobCreated.getValue())
            .key(jobCreated.getKey()));

    // then - the job is completed, but the task is terminated instead of being completed and the
    // job output is not triggered into the terminating scope
    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated()
                .withElementId("task"))
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .doesNotContain(
            ProcessInstanceIntent.COMPLETE_ELEMENT,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED);

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .filter(
                    r ->
                        r.getValueType() == ValueType.PROCESS_EVENT
                            && r.getIntent() == ProcessEventIntent.TRIGGERING))
        .isEmpty();
  }
}

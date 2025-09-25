/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.multiinstance;

import static io.camunda.zeebe.engine.common.processing.processinstance.ProcessInstanceBatchActivateProcessor.PARENT_NOT_FOUND_ERROR_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MultiInstanceConcurrencyTest {
  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().maxCommandsInBatch(1);

  private static final String PROCESS_ID = "processId";
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldRejectPiBatchActivateWhenBodyIsTerminated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("serviceTask1", t -> t.zeebeJobType("jobType1"))
                .sequenceFlowId("task1-to-task2")
                .serviceTask("serviceTask2", t -> t.zeebeJobType("jobType2"))
                .multiInstance(
                    mi ->
                        mi.zeebeInputCollectionExpression("[1,2,3]")
                            .zeebeInputElement("item")
                            .parallel())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();
    final var task1Job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var procDefKey = serviceTask.getValue().getProcessDefinitionKey();
    final ProcessInstanceRecord sequenceFlow =
        Records.processInstance(processInstanceKey, PROCESS_ID)
            .setBpmnElementType(BpmnElementType.SEQUENCE_FLOW)
            .setElementId("task1-to-task2")
            .setFlowScopeKey(processInstanceKey)
            .setProcessDefinitionKey(procDefKey);
    final var multiInstance =
        Records.processInstance(processInstanceKey, PROCESS_ID)
            .setBpmnElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .setElementId("serviceTask2")
            .setFlowScopeKey(processInstanceKey)
            .setProcessDefinitionKey(procDefKey);

    // when
    ENGINE.stop();
    RecordingExporter.reset();
    final var multiInstanceKey = task1Job.getKey() + 10000;
    final var batchActivateKey = multiInstanceKey + 1;
    // We need to ensure that the multi-instance body is terminated before we try to activate the
    // first batch. In order to achieve we have to write our events and commands manually. If we
    // don't do this the test won't be deterministic.
    ENGINE.writeRecords(
        RecordToWrite.event().key(task1Job.getKey()).job(JobIntent.COMPLETED, task1Job.getValue()),
        RecordToWrite.event()
            .key(serviceTask.getKey())
            .processInstance(ProcessInstanceIntent.ELEMENT_COMPLETING, serviceTask.getValue()),
        RecordToWrite.event()
            .key(serviceTask.getKey())
            .processInstance(ProcessInstanceIntent.ELEMENT_COMPLETED, serviceTask.getValue()),
        RecordToWrite.event()
            .key(-1L)
            .processInstance(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN, sequenceFlow),
        RecordToWrite.event()
            .key(multiInstanceKey)
            .processInstance(ProcessInstanceIntent.ELEMENT_ACTIVATING, multiInstance),
        RecordToWrite.event()
            .key(multiInstanceKey)
            .processInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED, multiInstance),
        RecordToWrite.command()
            .key(multiInstanceKey)
            .processInstance(ProcessInstanceIntent.TERMINATE_ELEMENT, multiInstance),
        RecordToWrite.command()
            .key(batchActivateKey)
            .processInstanceBatch(
                ProcessInstanceBatchIntent.ACTIVATE,
                Records.processInstanceBatch(processInstanceKey, multiInstanceKey)));
    ENGINE.start();

    // then
    assertThat(
            RecordingExporter.processInstanceBatchRecords(ProcessInstanceBatchIntent.ACTIVATE)
                .onlyCommandRejections()
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(batchActivateKey)
                .getFirst()
                .getRejectionReason())
        .isEqualTo(PARENT_NOT_FOUND_ERROR_MESSAGE.formatted(multiInstanceKey));
  }
}

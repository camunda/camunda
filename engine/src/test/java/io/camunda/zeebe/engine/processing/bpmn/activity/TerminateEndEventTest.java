/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.EndEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class TerminateEndEventTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCompleteProcessInstance() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .endEvent("terminate-end", EndEventBuilder::terminate)
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .describedAs(
            "Expect to complete the process instance when reaching the terminate end event")
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelRootElementInstance() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway("fork")
                .userTask("A")
                .endEvent("none-end")
                .moveToNode("fork")
                .serviceTask("B", serviceTask -> serviceTask.zeebeJobType("B"))
                .endEvent("terminate-end", EndEventBuilder::terminate)
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE_RULE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType(), Record::getIntent)
        .describedAs(
            "Expect to terminate all element instances when reaching the terminate end event")
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelElementInstanceInEmbeddedSubprocess() {
    // given
    final Consumer<SubProcessBuilder> subprocessBuilder =
        subprocess ->
            subprocess
                .embeddedSubProcess()
                .startEvent()
                .parallelGateway("subprocess_fork")
                .userTask("B")
                .endEvent("end_after_B")
                .moveToNode("subprocess_fork")
                .serviceTask("C", serviceTask -> serviceTask.zeebeJobType("C"))
                .endEvent("terminate-end", EndEventBuilder::terminate);

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("process_fork")
            .serviceTask("A", serviceTask -> serviceTask.zeebeJobType("A"))
            .endEvent("end_after_A")
            .moveToNode("process_fork")
            .subProcess("subprocess", subprocessBuilder)
            .sequenceFlowId("to_end_after_subprocess")
            .endEvent("end_after_subprocess")
            .done();

    ENGINE_RULE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE_RULE.job().ofInstance(processInstanceKey).withType("C").complete();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("end_after_subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(
            record -> record.getValue().getBpmnElementType(),
            record -> record.getValue().getElementId(),
            Record::getIntent)
        .describedAs(
            "Expect to terminate all element instances in the subprocess when reaching the terminate end event")
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, "C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                "terminate-end",
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.USER_TASK, "B", ProcessInstanceIntent.ELEMENT_TERMINATED))
        .describedAs("Expect to complete the subprocess and take the outgoing sequence flow")
        .containsSubsequence(
            tuple(
                BpmnElementType.SUB_PROCESS, "subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.SEQUENCE_FLOW,
                "to_end_after_subprocess",
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnElementType.END_EVENT,
                "end_after_subprocess",
                ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs(
            "Expect that the element instances outside of the subprocess are not terminated")
        .doesNotContain(
            tuple(BpmnElementType.SERVICE_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATED));

    ENGINE_RULE.job().ofInstance(processInstanceKey).withType("A").complete();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            record -> record.getValue().getBpmnElementType(),
            record -> record.getValue().getElementId(),
            Record::getIntent)
        .describedAs(
            "Expect to complete the process instance after all element instances are completed")
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, "A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT, "end_after_A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }
}

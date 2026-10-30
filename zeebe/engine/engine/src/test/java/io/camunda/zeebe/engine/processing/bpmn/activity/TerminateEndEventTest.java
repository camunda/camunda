/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.EndEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class TerminateEndEventTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  @Rule public final BrokerClassRuleHelper brokerClassRuleHelper = new BrokerClassRuleHelper();

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

  @Test
  public void shouldCancelElementInstanceInEventSubprocess() {
    // given
    final var messageName = brokerClassRuleHelper.getMessageName();
    final var correlationKey = brokerClassRuleHelper.getCorrelationValue();

    final Consumer<EventSubProcessBuilder> eventSubprocessBuilder =
        eventSubprocess ->
            eventSubprocess
                .startEvent()
                .interrupting(false)
                .message(message -> message.name(messageName).zeebeCorrelationKeyExpression("key"))
                .parallelGateway("fork")
                .userTask("B")
                .endEvent("end_after_B")
                .moveToNode("fork")
                .serviceTask("C", serviceTask -> serviceTask.zeebeJobType("C"))
                .endEvent("terminate-end", EndEventBuilder::terminate);

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess("event_subprocess", eventSubprocessBuilder)
            .startEvent()
            .serviceTask("A", serviceTask -> serviceTask.zeebeJobType("A"))
            .endEvent("end_after_A")
            .done();

    ENGINE_RULE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", correlationKey)
            .create();

    ENGINE_RULE
        .message()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofHours(1))
        .publish();

    ENGINE_RULE.job().ofInstance(processInstanceKey).withType("C").complete();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("event_subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(
            record -> record.getValue().getBpmnElementType(),
            record -> record.getValue().getElementId(),
            Record::getIntent)
        .describedAs(
            "Expect to terminate all element instances in the event subprocess when reaching the terminate end event")
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, "C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                "terminate-end",
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.USER_TASK, "B", ProcessInstanceIntent.ELEMENT_TERMINATED))
        .describedAs("Expect to complete the event subprocess")
        .contains(
            tuple(
                BpmnElementType.EVENT_SUB_PROCESS,
                "event_subprocess",
                ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs(
            "Expect that the element instances outside of the event subprocess are not terminated")
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

  @Test
  public void shouldCancelElementInstanceInMultiInstance() {
    // given
    final Consumer<SubProcessBuilder> subprocessBuilder =
        subprocess ->
            subprocess
                .embeddedSubProcess()
                .startEvent()
                .exclusiveGateway("split")
                // first iteration of the multi-instance
                .defaultFlow()
                .parallelGateway("fork_1")
                .userTask("A")
                .endEvent("end_after_A")
                .moveToNode("fork_1")
                .serviceTask("B", serviceTask -> serviceTask.zeebeJobType("B"))
                .endEvent("terminate_end_after_B", EndEventBuilder::terminate)
                // second iteration of the multi-instance
                .moveToNode("split")
                .conditionExpression("x = 2")
                .parallelGateway("fork_2")
                .userTask("C")
                .endEvent("end_after_C")
                .moveToNode("fork_2")
                .serviceTask("D", serviceTask -> serviceTask.zeebeJobType("D"))
                .endEvent("terminate_end_after_D", EndEventBuilder::terminate);

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("subprocess", subprocessBuilder)
            .multiInstance(
                multiInstance ->
                    multiInstance
                        .parallel()
                        .zeebeInputCollectionExpression("[1,2]")
                        .zeebeInputElement("x"))
            .sequenceFlowId("to_end_after_subprocess")
            .endEvent("end_after_subprocess")
            .done();

    ENGINE_RULE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(4))
        .describedAs("Assume that four jobs are created, two for each subprocess instance")
        .hasSize(4);

    // when
    ENGINE_RULE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(
            record -> record.getValue().getBpmnElementType(),
            record -> record.getValue().getElementId(),
            Record::getIntent)
        .describedAs(
            "Expect to terminate all element instances in the subprocess when reaching the terminate end event")
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, "B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                "terminate_end_after_B",
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.USER_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.SUB_PROCESS, "subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs("Expect that the other instance of the subprocess is not terminated")
        .doesNotContain(
            tuple(BpmnElementType.USER_TASK, "C", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SERVICE_TASK, "D", ProcessInstanceIntent.ELEMENT_TERMINATED));

    ENGINE_RULE.job().ofInstance(processInstanceKey).withType("D").complete();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            record -> record.getValue().getBpmnElementType(),
            record -> record.getValue().getElementId(),
            Record::getIntent)
        .describedAs("Expect to complete the other subprocess instance")
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, "D", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.USER_TASK, "C", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.SUB_PROCESS, "subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs(
            "Expect to complete the multi-instance after all subprocess instances are completed")
        .containsSubsequence(
            tuple(
                BpmnElementType.SUB_PROCESS, "subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.SUB_PROCESS, "subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.MULTI_INSTANCE_BODY,
                "subprocess",
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                "end_after_subprocess",
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelChildProcessInstance() {
    // given
    final var childProcessId = brokerClassRuleHelper.getBpmnProcessId();

    final var childProcess =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .parallelGateway("fork")
            .userTask("A")
            .endEvent("none-end")
            .moveToNode("fork")
            .serviceTask("B", serviceTask -> serviceTask.zeebeJobType("B"))
            .endEvent("terminate-end", EndEventBuilder::terminate)
            .done();

    final var parentProcess =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity("C", callActivity -> callActivity.zeebeProcessId(childProcessId))
            .sequenceFlowId("to_end_after_C")
            .endEvent("end_after_C")
            .done();

    ENGINE_RULE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcess)
        .withXmlResource("child.bpmn", childProcess)
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();

    ENGINE_RULE.job().ofInstance(childProcessInstanceKey).withType("B").complete();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKeyOrParentProcessInstanceKey(processInstanceKey)
                .limit(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(
            record -> record.getValue().getBpmnElementType(),
            record -> record.getValue().getElementId(),
            Record::getIntent)
        .describedAs(
            "Expect to terminate all element instances in the child process instance when reaching the terminate end event")
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, "B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.END_EVENT,
                "terminate-end",
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.USER_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATED))
        .describedAs("Expect to complete the child process instance and the call activity")
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, childProcessId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.CALL_ACTIVITY, "C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.SEQUENCE_FLOW,
                "to_end_after_C",
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnElementType.END_EVENT, "end_after_C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteProcessWhenWaitingAtParallelGateway() {
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway("fork")
                .parallelGateway("join")
                .moveToNode("fork")
                .serviceTask("A", s -> s.zeebeJobType("type"))
                .boundaryEvent()
                .error("code")
                .endEvent()
                .terminate()
                .moveToNode("A")
                .connectTo("join")
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE_RULE
        .job()
        .ofInstance(processInstanceKey)
        .withType("type")
        .withErrorCode("code")
        .throwError();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(record -> record.getValue().getBpmnElementType(), Record::getIntent)
        .describedAs(
            "Expect to terminate all element instances when reaching the terminate end event")
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.EmbeddedSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MultipleEventSubprocessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "proc";
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldTriggerMultipleEventSubprocesses() {
    final BpmnModelInstance model = twoEventSubprocModel(false, false, helper.getMessageName());
    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();
    triggerTimerStart(processInstanceKey);
    triggerMessageStart(processInstanceKey, helper.getMessageName());

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
        .withElementId("event_sub_proc_timer")
        .await();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
        .withElementId("event_sub_proc_msg")
        .await();
  }

  @Test
  public void shouldInterruptOtherActiveEventSubprocess() {
    // given
    final BpmnModelInstance model =
        twoEventSubprocWithTasksModel(false, true, helper.getMessageName());
    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    triggerTimerStart(processInstanceKey);
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("event_sub_task_timer")
                .exists())
        .describedAs("Expected service task after timer start event to exist")
        .isTrue();
    triggerMessageStart(processInstanceKey, helper.getMessageName());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getElementId(), r.getIntent()))
        .containsSubsequence(
            tuple("event_sub_proc_timer", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("event_sub_task_timer", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("event_sub_proc_timer", ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple("event_sub_task_timer", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple("event_sub_proc_timer", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple("event_sub_proc_msg", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("event_sub_proc_msg", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCloseEventSubscriptionWhenScopeCloses() {
    final BpmnModelInstance model = nestedMsgModel(helper.getMessageName());
    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .describedAs("Expected event subprocess message start subscription to be opened.")
        .isTrue();
    completeJob(processInstanceKey, "sub_proc_type");

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(helper.getMessageName())
                .exists())
        .describedAs("Expected event subprocess start message subscription to be closed.")
        .isTrue();
  }

  @Test
  public void shouldCorrelateTwoMessagesIfNonInterrupting() {
    // given
    final BpmnModelInstance model =
        twoEventSubprocWithTasksModel(false, false, helper.getMessageName());
    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();
    triggerMessageStart(processInstanceKey, helper.getMessageName());
    triggerMessageStart(processInstanceKey, helper.getMessageName());

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldKeepProcessInstanceActive() {
    // given
    final BpmnModelInstance model =
        twoEventSubprocWithTasksModel(false, false, helper.getMessageName());
    ENGINE.deployment().withXmlResource(model).deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();
    triggerTimerStart(processInstanceKey);

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType("timerTask")
        .await();

    // when
    completeJob(processInstanceKey, "type");

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.END_EVENT)
        .withElementId("end_proc")
        .await();

    completeJob(processInstanceKey, "timerTask");
    assertThat(RecordingExporter.processInstanceRecords().limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getElementId(), r.getIntent()))
        .containsSubsequence(
            tuple("event_sub_task_timer", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("end_proc", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("event_sub_task_timer", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTerminateEventSubprocessIfScopeTerminates() {
    // given
    final BpmnModelInstance model =
        twoEventSubprocWithTasksModel(false, false, helper.getMessageName());
    ENGINE.deployment().withXmlResource(model).deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();
    triggerTimerStart(processInstanceKey);

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType("timerTask")
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(RecordingExporter.processInstanceRecords().limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldOnlyInterruptOnce() {
    // given
    final BpmnModelInstance model =
        twoEventSubprocWithTasksModel(true, true, helper.getMessageName());
    ENGINE.deployment().withXmlResource(model).deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("key", 123))
            .create();

    triggerTimerStart(processInstanceKey);
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("event_sub_start_timer")
        .await();

    // when
    triggerMessageStart(processInstanceKey, helper.getMessageName());

    ENGINE.job().ofInstance(processInstanceKey).withType("timerTask").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS))
        .extracting(r -> r.getValue().getElementId())
        .containsExactly("event_sub_proc_timer");
  }

  private void triggerMessageStart(final long processInstanceKey, final String msgName) {
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    ENGINE.message().withName(msgName).withCorrelationKey("123").publish();
  }

  private void triggerTimerStart(final long processInstanceKey) {
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .await();

    ENGINE.increaseTime(Duration.ofSeconds(60));
  }

  private static void completeJob(final long processInstanceKey, final String taskType) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(taskType)
        .await();

    ENGINE.job().ofInstance(processInstanceKey).withType(taskType).complete();
  }

  private BpmnModelInstance twoEventSubprocModel(
      final boolean timerInterrupt, final boolean msgInterrupt, final String msgName) {
    final ProcessBuilder builder = Bpmn.createExecutableProcess(PROCESS_ID);

    builder
        .eventSubProcess("event_sub_proc_timer")
        .startEvent("event_sub_start_timer")
        .interrupting(timerInterrupt)
        .timerWithDuration("PT60S")
        .endEvent("event_sub_end_timer");
    builder
        .eventSubProcess("event_sub_proc_msg")
        .startEvent("event_sub_start_msg")
        .interrupting(msgInterrupt)
        .message(b -> b.name(msgName).zeebeCorrelationKeyExpression("key"))
        .endEvent("event_sub_end_msg");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeJobType("type"))
        .endEvent("end_proc")
        .done();
  }

  private BpmnModelInstance twoEventSubprocWithTasksModel(
      final boolean timerInterrupt, final boolean msgInterrupt, final String msgName) {
    final ProcessBuilder builder = Bpmn.createExecutableProcess(PROCESS_ID);

    builder
        .eventSubProcess("event_sub_proc_timer")
        .startEvent("event_sub_start_timer")
        .interrupting(timerInterrupt)
        .timerWithDuration("PT60S")
        .serviceTask("event_sub_task_timer", b -> b.zeebeJobType("timerTask"))
        .endEvent("event_sub_end_timer");
    builder
        .eventSubProcess("event_sub_proc_msg")
        .startEvent("event_sub_start_msg")
        .interrupting(msgInterrupt)
        .message(b -> b.name(msgName).zeebeCorrelationKeyExpression("key"))
        .endEvent("event_sub_end_msg");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeJobType("type"))
        .endEvent("end_proc")
        .done();
  }

  private static BpmnModelInstance nestedMsgModel(final String msgName) {
    final StartEventBuilder procBuilder =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent("proc_start");
    procBuilder.serviceTask("proc_task", b -> b.zeebeJobType("proc_type")).endEvent();
    final EmbeddedSubProcessBuilder subProcBuilder =
        procBuilder.subProcess("sub_proc").embeddedSubProcess();

    subProcBuilder
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .interrupting(true)
        .message(b -> b.name(msgName).zeebeCorrelationKeyExpression("key"))
        .endEvent("event_sub_end");
    return subProcBuilder
        .startEvent("sub_start")
        .serviceTask("sub_proc_task", t -> t.zeebeJobType("sub_proc_type"))
        .endEvent("sub_end")
        .done();
  }
}

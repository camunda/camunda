/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.EmbeddedSubProcessBuilder;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.model.bpmn.builder.StartEventBuilder;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.test.util.BrokerClassRuleHelper;
import io.zeebe.test.util.record.RecordingExporter;
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
    final long wfInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();
    triggerTimerStart(wfInstanceKey);
    triggerMessageStart(wfInstanceKey, helper.getMessageName());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(wfInstanceKey)
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
                .limit(8))
        .extracting(r -> tuple(r.getValue().getElementId(), r.getIntent()))
        .containsSubsequence(
            tuple("event_sub_proc_timer", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("event_sub_proc_timer", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .containsSubsequence(
            tuple("event_sub_proc_msg", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("event_sub_proc_msg", ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInterruptOtherActiveEventSubprocess() {
    // given
    final BpmnModelInstance model =
        twoEventSubprocWithTasksModel(false, true, helper.getMessageName());
    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    final long wfInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    triggerTimerStart(wfInstanceKey);
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(wfInstanceKey)
                .withElementId("event_sub_task_timer")
                .exists())
        .describedAs("Expected service task after timer start event to exist")
        .isTrue();
    triggerMessageStart(wfInstanceKey, helper.getMessageName());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(wfInstanceKey)
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
    final long wfInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(wfInstanceKey)
                .exists())
        .describedAs("Expected event subprocess message start subscription to be opened.")
        .isTrue();
    completeJob(wfInstanceKey, "sub_proc_type");

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(wfInstanceKey)
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
    final long wfInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();
    triggerMessageStart(wfInstanceKey, helper.getMessageName());
    triggerMessageStart(wfInstanceKey, helper.getMessageName());

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(wfInstanceKey)
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

    final long wfInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();
    triggerTimerStart(wfInstanceKey);

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(wfInstanceKey)
        .withType("timerTask")
        .await();

    // when
    completeJob(wfInstanceKey, "type");

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(wfInstanceKey)
        .withElementType(BpmnElementType.END_EVENT)
        .withElementId("end_proc")
        .await();

    completeJob(wfInstanceKey, "timerTask");
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

    final long wfInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();
    triggerTimerStart(wfInstanceKey);

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(wfInstanceKey)
        .withType("timerTask")
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(wfInstanceKey).cancel();

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
    final long wfInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("key", 123))
            .create();

    triggerTimerStart(wfInstanceKey);
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ACTIVATE_ELEMENT)
        .withProcessInstanceKey(wfInstanceKey)
        .withElementId("event_sub_start_timer")
        .await();

    // when
    triggerMessageStart(wfInstanceKey, helper.getMessageName());

    ENGINE.job().ofInstance(wfInstanceKey).withType("timerTask").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(wfInstanceKey)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS))
        .extracting(r -> r.getValue().getElementId())
        .containsExactly("event_sub_proc_timer");
  }

  private void triggerMessageStart(final long wfInstanceKey, final String msgName) {
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(wfInstanceKey)
        .await();

    ENGINE.message().withName(msgName).withCorrelationKey("123").publish();
  }

  private void triggerTimerStart(final long wfInstanceKey) {
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(wfInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .await();

    ENGINE.increaseTime(Duration.ofSeconds(60));
  }

  private static void completeJob(final long wfInstanceKey, final String taskType) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(wfInstanceKey)
        .withType(taskType)
        .await();

    ENGINE.job().ofInstance(wfInstanceKey).withType(taskType).complete();
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

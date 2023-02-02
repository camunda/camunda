/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InterruptingEventSubprocessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "proc";
  private static final String JOB_TYPE = "type";
  private static final String MESSAGE_CORRELATION_KEY = "123";

  private static String messageName;

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Parameterized.Parameter public String testName;

  @Parameterized.Parameter(1)
  public Function<StartEventBuilder, StartEventBuilder> builder;

  @Parameterized.Parameter(2)
  public Consumer<Long> triggerEventSubprocess;

  private ProcessMetadataValue currentProcess;

  @Parameterized.Parameters(name = "{0} event subprocess")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "timer",
        eventSubprocess(s -> s.timerWithDuration("PT60S")),
        eventTrigger(
            key -> {
              assertThat(
                      RecordingExporter.timerRecords(TimerIntent.CREATED)
                          .withProcessInstanceKey(key)
                          .exists())
                  .describedAs("Expected timer to exist")
                  .isTrue();
              ENGINE.increaseTime(Duration.ofSeconds(60));
            })
      },
      {
        "message",
        eventSubprocess(
            s -> s.message(b -> b.name(messageName).zeebeCorrelationKeyExpression("key"))),
        eventTrigger(
            key -> {
              RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                  .withProcessInstanceKey(key)
                  .withMessageName(messageName)
                  .await();
              ENGINE
                  .message()
                  .withName(messageName)
                  .withCorrelationKey(MESSAGE_CORRELATION_KEY)
                  .publish();
            })
      },
      {
        "error",
        eventSubprocess(s -> s.error("ERROR")),
        eventTrigger(
            key ->
                ENGINE.job().ofInstance(key).withType(JOB_TYPE).withErrorCode("ERROR").throwError())
      },
    };
  }

  private static Function<StartEventBuilder, StartEventBuilder> eventSubprocess(
      final Function<StartEventBuilder, StartEventBuilder> consumer) {
    return consumer;
  }

  private static Consumer<Long> eventTrigger(final Consumer<Long> eventTrigger) {
    return eventTrigger;
  }

  @Before
  public void init() {
    messageName = helper.getMessageName();
  }

  @Test
  public void shouldTriggerEventSubprocess() {
    // when
    final BpmnModelInstance model = process(withEventSubprocess(builder));
    final long processInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    final Record<ProcessInstanceRecordValue> startEventActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("event_sub_start")
            .withElementType(BpmnElementType.START_EVENT)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> subProcessActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("event_sub_proc")
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(startEventActivated.getValue())
        .hasProcessDefinitionKey(currentProcess.getProcessDefinitionKey())
        .hasProcessInstanceKey(processInstanceKey)
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasElementId("event_sub_start")
        .hasVersion(currentProcess.getVersion())
        .hasFlowScopeKey(subProcessActivated.getKey());

    assertEventSubprocessLifecycle(processInstanceKey);
  }

  @Test
  public void shouldTriggerEventSubprocessAndCreateLocalScopeVariable() {
    // given
    final BpmnModelInstance model = process(withEventSubprocessAndLocalScopeVariable(builder));

    // when
    final long processInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    final Record<ProcessInstanceRecordValue> subProcessActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("event_sub_proc")
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertEventSubprocessLifecycle(processInstanceKey);

    RecordingExporter.variableRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withName("localScope")
        .withScopeKey(subProcessActivated.getKey())
        .await();
  }

  @Test
  public void shouldInterruptAndCompleteParent() {
    // given
    final BpmnModelInstance model = process(withEventSubprocess(builder));
    final long processInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInterruptExecutionWaitingOnParallelGateway() {
    // given
    final var process =
        withEventSubprocess(builder)
            .startEvent("start_proc")
            .parallelGateway("fork")
            .serviceTask("task-1", t -> t.zeebeJobType("task-1"))
            .sequenceFlowId("task-1-to-join")
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask("task-2", t -> t.zeebeJobType(JOB_TYPE))
            .sequenceFlowId("task-2-to-join")
            .connectTo("join")
            .endEvent("end_proc")
            .done();

    final long processInstanceKey = createInstanceAndWaitForTask(process);

    ENGINE.job().ofInstance(processInstanceKey).withType("task-1").complete();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task-1-to-join")
        .await();

    triggerEventSubprocess.accept(processInstanceKey);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInterruptNestedSubprocess() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubprocess =
        eventSubProcess ->
            builder
                .apply(eventSubProcess.startEvent("event_sub_start").interrupting(true))
                .endEvent("event_sub_end");

    final Consumer<SubProcessBuilder> embeddedSubprocess =
        subProcess ->
            subProcess
                .embeddedSubProcess()
                .eventSubProcess("event_sub_proc", eventSubprocess)
                .startEvent("sub_start")
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent("sub_end");

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("proc_start")
            .subProcess("sub_proc", embeddedSubprocess)
            .endEvent("end_proc")
            .done();

    final long processInstanceKey = createInstanceAndTriggerEvent(process);

    // then
    final Record<ProcessInstanceRecordValue> subProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.COMPLETE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("sub_proc")
            .getFirst();

    final Record<ProcessInstanceRecordValue> eventSubproc =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.COMPLETE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("event_sub_proc")
            .getFirst();

    assertThat(eventSubproc.getValue().getFlowScopeKey()).isEqualTo(subProcess.getKey());
    assertThat(subProcess.getValue().getFlowScopeKey()).isEqualTo(processInstanceKey);
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("task")
                .getFirst())
        .isNotNull();
  }

  @Test
  public void shouldHaveScopeVariableIfInterrupting() {
    // given
    final BpmnModelInstance model = process(withEventSubprocessTask(builder, helper.getJobType()));
    final long processInstanceKey = createInstanceAndWaitForTask(model);

    final long procTaskKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst()
            .getKey();
    ENGINE
        .variables()
        .ofScope(procTaskKey)
        .withDocument(Map.of("y", 2))
        .withUpdateSemantic(VariableDocumentUpdateSemantic.LOCAL)
        .update();

    // when
    triggerEventSubprocess.accept(processInstanceKey);
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType(helper.getJobType())
                .exists())
        .isTrue();

    // then
    final Record<JobBatchRecordValue> job = ENGINE.jobs().withType(helper.getJobType()).activate();
    final Map<String, Object> jobVariables =
        job.getValue().getJobs().iterator().next().getVariables();
    assertThat(jobVariables).containsOnly(Map.entry("key", MESSAGE_CORRELATION_KEY));
  }

  @Test
  public void shouldNotPropagateVariablesToScope() {
    // given
    final BpmnModelInstance model = process(withEventSubprocessTask(builder, helper.getJobType()));
    final long processInstanceKey = createInstanceAndTriggerEvent(model);
    final long eventSubprocKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .getFirst()
            .getKey();

    // when
    ENGINE
        .variables()
        .ofScope(eventSubprocKey)
        .withDocument(Map.of("y", 2))
        .withUpdateSemantic(VariableDocumentUpdateSemantic.LOCAL)
        .update();
    ENGINE.job().ofInstance(processInstanceKey).withType(helper.getJobType()).complete();

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords()
                .withScopeKey(processInstanceKey))
        .extracting(r -> r.getValue().getName())
        .doesNotContain("y");
  }

  @Test
  public void shouldCloseEventSubscriptions() {
    // given
    final var eventSubprocess = withEventSubprocess(builder);

    eventSubprocess
        .eventSubProcess(
            "message-event-subprocess",
            s ->
                s.startEvent()
                    .message(m -> m.name("other-message").zeebeCorrelationKeyExpression("key"))
                    .endEvent())
        .eventSubProcess(
            "timer-event-subprocess",
            s -> s.startEvent("other-timer").timerWithDuration("P1D").endEvent());

    final long processInstanceKey = createInstanceAndWaitForTask(process(eventSubprocess));

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName("other-message")
        .await();

    triggerEventSubprocess.accept(processInstanceKey);

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("other-message")
                .findFirst())
        .describedAs("Expected the message subscription to be deleted")
        .isPresent();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withHandlerNodeId("other-timer")
                .findFirst())
        .describedAs("Expected the timer to be canceled")
        .isPresent();
  }

  @Test
  public void shouldNotCloseEventSubscriptionsOfBoundaryEvent() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubprocess =
        eventSubProcess ->
            builder
                .apply(eventSubProcess.startEvent().interrupting(true))
                .serviceTask("event_sub_task", t -> t.zeebeJobType("event_sub_task"));

    final Consumer<SubProcessBuilder> embeddedSubprocess =
        subProcess ->
            subProcess
                .embeddedSubProcess()
                .eventSubProcess("event_sub_proc", eventSubprocess)
                .startEvent()
                .serviceTask("sub_task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("sub_proc", embeddedSubprocess)
            .boundaryEvent()
            .message(m -> m.name("boundary").zeebeCorrelationKeyExpression("key"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", MESSAGE_CORRELATION_KEY)
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(JOB_TYPE)
        .await();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName("boundary")
        .await();

    // when
    triggerEventSubprocess.accept(processInstanceKey);

    // then
    final var serviceTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("event_sub_task")
            .getFirst();

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= serviceTaskActivated.getPosition())
                .processMessageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("boundary"))
        .extracting(Record::getIntent)
        .describedAs("Expected the boundary event subscription to be open")
        .contains(ProcessMessageSubscriptionIntent.CREATED)
        .doesNotContain(ProcessMessageSubscriptionIntent.DELETED);
  }

  @Test
  public void shouldTriggerInterruptingEventSubprocessAndInterruptingBoundaryEvent() {
    // given
    final var boundaryEventMessageName = "boundary-" + helper.getMessageName();

    final Consumer<EventSubProcessBuilder> eventSubprocess =
        eventSubProcess ->
            builder
                .apply(eventSubProcess.startEvent().interrupting(true))
                .serviceTask("event_sub_task", t -> t.zeebeJobType("event_sub_task"));

    final Consumer<SubProcessBuilder> embeddedSubprocess =
        subProcess ->
            subProcess
                .embeddedSubProcess()
                .eventSubProcess("event_sub_proc", eventSubprocess)
                .startEvent()
                .serviceTask("sub_task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("sub_proc", embeddedSubprocess)
            .boundaryEvent()
            .cancelActivity(true)
            .message(m -> m.name(boundaryEventMessageName).zeebeCorrelationKeyExpression("key"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", MESSAGE_CORRELATION_KEY)
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(JOB_TYPE)
        .await();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName(boundaryEventMessageName)
        .await();

    // when
    triggerEventSubprocess.accept(processInstanceKey);

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withElementId("event_sub_task")
        .await();

    ENGINE
        .message()
        .withName(boundaryEventMessageName)
        .withCorrelationKey(MESSAGE_CORRELATION_KEY)
        .publish();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .describedAs("Expected the boundary event to be triggered")
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerInterruptingEventSubprocessAndNonInterruptingBoundaryEvent() {
    // given
    final var boundaryEventMessageName = "boundary-" + helper.getMessageName();

    final Consumer<EventSubProcessBuilder> eventSubprocess =
        eventSubProcess ->
            builder
                .apply(eventSubProcess.startEvent().interrupting(true))
                .serviceTask("event_sub_task", t -> t.zeebeJobType("event_sub_task"));

    final Consumer<SubProcessBuilder> embeddedSubprocess =
        subProcess ->
            subProcess
                .embeddedSubProcess()
                .eventSubProcess("event_sub_proc", eventSubprocess)
                .startEvent()
                .serviceTask("sub_task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("sub_proc", embeddedSubprocess)
            .boundaryEvent()
            .cancelActivity(false)
            .message(m -> m.name(boundaryEventMessageName).zeebeCorrelationKeyExpression("key"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", MESSAGE_CORRELATION_KEY)
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(JOB_TYPE)
        .await();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName(boundaryEventMessageName)
        .await();

    // when
    triggerEventSubprocess.accept(processInstanceKey);

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withElementId("event_sub_task")
        .await();

    ENGINE
        .message()
        .withName(boundaryEventMessageName)
        .withCorrelationKey(MESSAGE_CORRELATION_KEY)
        .publish();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.BOUNDARY_EVENT)
        .await();

    ENGINE.job().ofInstance(processInstanceKey).withType("event_sub_task").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerEmbeddedInterruptingEventSubprocessOnlyOnce() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubprocess =
        eventSubProcess ->
            builder
                .apply(eventSubProcess.startEvent().interrupting(true))
                .serviceTask("event_sub_task", t -> t.zeebeJobType("event_sub_task"));

    final Consumer<SubProcessBuilder> embeddedSubprocess =
        subProcess ->
            subProcess
                .embeddedSubProcess()
                .eventSubProcess("event_sub_proc", eventSubprocess)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("sub_task1", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .moveToNode("fork")
                .serviceTask("sub_task2", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("sub_proc", embeddedSubprocess)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", MESSAGE_CORRELATION_KEY)
            .create();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType(JOB_TYPE)
                .limit(2)
                .count())
        .describedAs("Await until both tasks are activated")
        .isEqualTo(2);

    // when
    triggerEventSubprocess.accept(processInstanceKey);

    ENGINE.job().ofInstance(processInstanceKey).withType("event_sub_task").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .describedAs("Expected to activate the event subprocess only once")
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerRootInterruptingEventSubprocessOnlyOnce() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubprocess =
        eventSubProcess ->
            builder
                .apply(eventSubProcess.startEvent().interrupting(true))
                .serviceTask("event_sub_task", t -> t.zeebeJobType("event_sub_task"));

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess("event_sub_proc", eventSubprocess)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask("sub_task1", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .moveToNode("fork")
            .serviceTask("sub_task2", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", MESSAGE_CORRELATION_KEY)
            .create();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType(JOB_TYPE)
                .limit(2)
                .count())
        .describedAs("Await until both tasks are activated")
        .isEqualTo(2);

    // when
    triggerEventSubprocess.accept(processInstanceKey);

    ENGINE.job().ofInstance(processInstanceKey).withType("event_sub_task").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .describedAs("Expected to activate the event subprocess only once")
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private static void assertEventSubprocessLifecycle(final long processInstanceKey) {
    final List<Record<ProcessInstanceRecordValue>> events =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .onlyEvents()
            .filter(r -> r.getValue().getElementId().startsWith("event_sub_"))
            .limit(
                r ->
                    r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                        && r.getValue().getBpmnElementType() == BpmnElementType.EVENT_SUB_PROCESS)
            .asList();

    assertThat(events)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsExactly(
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATING, "event_sub_proc"),
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATED, "event_sub_proc"),
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATING, "event_sub_start"),
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATED, "event_sub_start"),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETING, "event_sub_start"),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, "event_sub_start"),
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATING, "event_sub_end"),
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATED, "event_sub_end"),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETING, "event_sub_end"),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, "event_sub_end"),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETING, "event_sub_proc"),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, "event_sub_proc"));
  }

  private long createInstanceAndTriggerEvent(final BpmnModelInstance model) {
    final long processInstanceKey = createInstanceAndWaitForTask(model);
    triggerEventSubprocess.accept(processInstanceKey);
    return processInstanceKey;
  }

  private long createInstanceAndWaitForTask(final BpmnModelInstance model) {
    currentProcess =
        ENGINE
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("key", MESSAGE_CORRELATION_KEY))
            .create();
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .describedAs("Expected job to be created")
        .isTrue();
    return processInstanceKey;
  }

  private static BpmnModelInstance process(final ProcessBuilder processBuilder) {
    return processBuilder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
        .endEvent("end_proc")
        .done();
  }

  private static ProcessBuilder withEventSubprocess(
      final Function<StartEventBuilder, StartEventBuilder> builder) {
    final ProcessBuilder process = Bpmn.createExecutableProcess(PROCESS_ID);

    builder
        .apply(
            process
                .eventSubProcess("event_sub_proc")
                .startEvent("event_sub_start")
                .interrupting(true))
        .endEvent("event_sub_end");

    return process;
  }

  private static ProcessBuilder withEventSubprocessAndLocalScopeVariable(
      final Function<StartEventBuilder, StartEventBuilder> builder) {
    final ProcessBuilder process = Bpmn.createExecutableProcess(PROCESS_ID);

    builder
        .apply(
            process
                .eventSubProcess("event_sub_proc")
                .zeebeInputExpression("=null", "localScope")
                .startEvent("event_sub_start")
                .interrupting(true))
        .endEvent("event_sub_end");

    return process;
  }

  private static ProcessBuilder withEventSubprocessTask(
      final Function<StartEventBuilder, StartEventBuilder> builder, final String jobType) {
    final ProcessBuilder process = Bpmn.createExecutableProcess(PROCESS_ID);

    builder
        .apply(
            process
                .eventSubProcess("event_sub_proc")
                .startEvent("event_sub_start")
                .interrupting(true))
        .serviceTask("event_sub_task", t -> t.zeebeJobType(jobType))
        .endEvent("event_sub_end");

    return process;
  }
}

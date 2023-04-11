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
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
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
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonInterruptingEventSubprocessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "proc";
  private static final String CORRELATION_KEY = "123";

  private static String messageName;

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Parameterized.Parameter public String testName;

  @Parameterized.Parameter(1)
  public Function<StartEventBuilder, StartEventBuilder> builder;

  @Parameterized.Parameter(2)
  public Consumer<Long> triggerEventSubprocess;

  @Parameterized.Parameter(3)
  public Boolean cyclic;

  private ProcessMetadataValue currentProcess;
  private String correlationKey;

  @Parameterized.Parameters(name = "{0} event subprocess")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "timer with duration",
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
            }),
        false
      },
      {
        "timer with date",
        eventSubprocess(s -> s.timerWithDateExpression("now() + duration(\"PT1M\")")),
        eventTrigger(
            key -> {
              assertThat(
                      RecordingExporter.timerRecords(TimerIntent.CREATED)
                          .withProcessInstanceKey(key)
                          .exists())
                  .describedAs("Expected timer to exist")
                  .isTrue();
              ENGINE.increaseTime(Duration.ofSeconds(60));
            }),
        false
      },
      {
        "timer with cycle",
        eventSubprocess(s -> s.timerWithCycle("R/PT60S")),
        eventTrigger(
            key -> {
              assertThat(
                      RecordingExporter.timerRecords(TimerIntent.CREATED)
                          .withProcessInstanceKey(key)
                          .exists())
                  .describedAs("Expected timer to exist")
                  .isTrue();
              ENGINE.increaseTime(Duration.ofSeconds(60));
            }),
        true
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
                  .withCorrelationKey("message-" + CORRELATION_KEY)
                  .publish();
            }),
        true
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
    correlationKey = String.format("%s-%s", testName, CORRELATION_KEY);
  }

  @Test
  public void shouldTriggerEventSubprocess() {
    // when
    final BpmnModelInstance model = eventSubprocModel(builder);
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
  public void shouldTriggerEventSubprocessTwice() {
    // Only run test if test-case is cyclic
    org.junit.Assume.assumeTrue(cyclic);

    // given
    final BpmnModelInstance model = eventSubprocModel(builder);
    final long processInstanceKey = createInstanceAndTriggerEvent(model);
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementId("event_sub_start")
        .withElementType(BpmnElementType.START_EVENT)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    triggerEventSubprocess.accept(processInstanceKey);

    // then
    final var startEventCount =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("event_sub_start")
            .withElementType(BpmnElementType.START_EVENT)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .count();
    assertThat(startEventCount).isEqualTo(2);
  }

  @Test
  public void shouldTriggerEventSubprocessAndCreateLocalScopeVariable() {
    // given
    final BpmnModelInstance model = eventSubprocModelWithLocalScopeVariable(builder);

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
  public void shouldTriggerEventSubprocessTwiceWithOwnLocalScopeVariable() {
    // Only run test if test-case is cyclic
    org.junit.Assume.assumeTrue(cyclic);

    // given
    final BpmnModelInstance model = eventSubprocModelWithLocalScopeVariable(builder);
    final long processInstanceKey = createInstanceAndTriggerEvent(model);
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementId("event_sub_start")
        .withElementType(BpmnElementType.START_EVENT)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    triggerEventSubprocess.accept(processInstanceKey);

    // then
    final List<Record<ProcessInstanceRecordValue>> eventSubProcessActivatedList =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("event_sub_proc")
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .collect(Collectors.toList());

    RecordingExporter.variableRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withName("localScope")
        .withScopeKey(eventSubProcessActivatedList.get(0).getKey())
        .await();

    RecordingExporter.variableRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withName("localScope")
        .withScopeKey(eventSubProcessActivatedList.get(1).getKey())
        .await();
  }

  @Test
  public void shouldNotInterruptParentProcess() {
    // when
    final BpmnModelInstance model = eventSubprocModel(builder);
    final long processInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    assertEventSubprocessLifecycle(processInstanceKey);
    ENGINE.job().ofInstance(processInstanceKey).withType("type").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  /** Specifically reproduces https://github.com/camunda/zeebe/issues/7097 */
  @Test
  public void shouldInterruptEmbeddedSubProcess() {
    // when
    final BpmnModelInstance model = eventSubprocModelWithEmbeddedSubWithBoundaryEvent(builder);
    final long processInstanceKey = createInstanceAndTriggerEvent(model);

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withElementId("embedded_sub_task")
        .await();

    // when
    ENGINE.message().withName("bndr").withCorrelationKey(correlationKey).publish();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .limit(
                    r ->
                        r.getValue().getBpmnElementType() == BpmnElementType.EVENT_SUB_PROCESS
                            && r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotPropagateVariablesToScope() {
    // given
    final BpmnModelInstance model = eventSubProcTaskModel(helper.getJobType(), "sub_type");
    final long processInstanceKey = createInstanceAndTriggerEvent(model);
    final long eventSubprocKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .getFirst()
            .getKey();

    ENGINE
        .variables()
        .ofScope(eventSubprocKey)
        .withDocument(Map.of("y", 2))
        .withUpdateSemantic(VariableDocumentUpdateSemantic.LOCAL)
        .update();
    ENGINE.job().ofInstance(processInstanceKey).withType("sub_type").complete();

    // then
    final Record<JobBatchRecordValue> job = ENGINE.jobs().withType(helper.getJobType()).activate();
    final Map<String, Object> jobVariables =
        job.getValue().getJobs().iterator().next().getVariables();

    // when
    assertThat(jobVariables).containsOnly(Map.entry("key", correlationKey));
  }

  private static void assertEventSubprocessLifecycle(final long processInstanceKey) {
    final List<Record<ProcessInstanceRecordValue>> events =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .filter(r -> r.getValue().getElementId().startsWith("event_sub_"))
            .onlyEvents()
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
            .withVariables(Map.of("key", correlationKey))
            .create();
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .describedAs("Expected job to be created")
        .isTrue();

    triggerEventSubprocess.accept(processInstanceKey);
    return processInstanceKey;
  }

  private static BpmnModelInstance eventSubprocModel(
      final Function<StartEventBuilder, StartEventBuilder> startBuilder) {
    final ProcessBuilder builder = Bpmn.createExecutableProcess(PROCESS_ID);
    startBuilder
        .apply(
            builder
                .eventSubProcess("event_sub_proc")
                .startEvent("event_sub_start")
                .interrupting(false))
        .endEvent("event_sub_end");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeJobType("type"))
        .endEvent("end_proc")
        .done();
  }

  private static BpmnModelInstance eventSubprocModelWithLocalScopeVariable(
      final Function<StartEventBuilder, StartEventBuilder> startBuilder) {
    final ProcessBuilder builder = Bpmn.createExecutableProcess(PROCESS_ID);
    startBuilder
        .apply(
            builder
                .eventSubProcess("event_sub_proc")
                .zeebeInputExpression("=null", "localScope")
                .startEvent("event_sub_start")
                .interrupting(false))
        .endEvent("event_sub_end");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeJobType("type"))
        .endEvent("end_proc")
        .done();
  }

  private BpmnModelInstance eventSubProcTaskModel(
      final String procTaskType, final String subprocTaskType) {
    final ProcessBuilder modelBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
    builder
        .apply(
            modelBuilder
                .eventSubProcess("event_sub_proc")
                .startEvent("event_sub_start")
                .interrupting(false))
        .serviceTask("event_sub_task", t -> t.zeebeJobType(subprocTaskType))
        .endEvent("event_sub_end");

    return modelBuilder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeJobType(procTaskType))
        .endEvent("end_proc")
        .done();
  }

  private static BpmnModelInstance eventSubprocModelWithEmbeddedSubWithBoundaryEvent(
      final Function<StartEventBuilder, StartEventBuilder> startBuilder) {
    final var builder = Bpmn.createExecutableProcess(PROCESS_ID);
    startBuilder
        .apply(
            builder
                .eventSubProcess("event_sub_proc")
                .startEvent("event_sub_start")
                .interrupting(false))
        .subProcess(
            "embedded",
            s ->
                s.boundaryEvent(
                    "boundary-msg",
                    msg ->
                        msg.message(m -> m.name("bndr").zeebeCorrelationKeyExpression("key"))
                            .cancelActivity(true)
                            .endEvent("boundary-end")))
        .embeddedSubProcess()
        .startEvent("embedded_sub_start")
        .serviceTask("embedded_sub_task", t -> t.zeebeJobType("embed"))
        .endEvent("embedded_sub_end")
        .moveToNode("embedded")
        .endEvent("event_sub_end");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeJobType("type"))
        .endEvent("end_proc")
        .done();
  }
}

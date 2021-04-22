/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.model.bpmn.builder.StartEventBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobBatchRecordValue;
import io.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.record.value.deployment.DeployedProcess;
import io.zeebe.test.util.BrokerClassRuleHelper;
import io.zeebe.test.util.record.RecordingExporter;
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

  private static String messageName;

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Parameterized.Parameter public String testName;

  @Parameterized.Parameter(1)
  public Function<StartEventBuilder, StartEventBuilder> builder;

  @Parameterized.Parameter(2)
  public Consumer<Long> triggerEventSubprocess;

  private DeployedProcess currentProcess;

  @Parameterized.Parameters(name = "{0} event subprocess")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "timer",
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
              ENGINE.message().withName(messageName).withCorrelationKey("123").publish();
            })
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
    final BpmnModelInstance model = eventSubprocModel(builder);
    final long wfInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    final Record<ProcessInstanceRecordValue> startEventActivate =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withElementId("event_sub_start")
            .withElementType(BpmnElementType.START_EVENT)
            .withProcessInstanceKey(wfInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> subProcessActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("event_sub_proc")
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .withProcessInstanceKey(wfInstanceKey)
            .getFirst();
    Assertions.assertThat(startEventActivate.getValue())
        .hasProcessDefinitionKey(currentProcess.getProcessDefinitionKey())
        .hasProcessInstanceKey(wfInstanceKey)
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasElementId("event_sub_start")
        .hasVersion(currentProcess.getVersion())
        .hasFlowScopeKey(subProcessActivated.getKey());

    assertEventSubprocessLifecycle(wfInstanceKey);
  }

  @Test
  public void shouldTriggerEventSubprocessTwice() {
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
    final long wfInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    assertEventSubprocessLifecycle(wfInstanceKey);
    ENGINE.job().ofInstance(wfInstanceKey).withType("type").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(wfInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotPropagateVariablesToScope() {
    // given
    final BpmnModelInstance model = eventSubProcTaskModel(helper.getJobType(), "sub_type");
    final long wfInstanceKey = createInstanceAndTriggerEvent(model);
    final long eventSubprocKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(wfInstanceKey)
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .getFirst()
            .getKey();

    ENGINE
        .variables()
        .ofScope(eventSubprocKey)
        .withDocument(Map.of("y", 2))
        .withUpdateSemantic(VariableDocumentUpdateSemantic.LOCAL)
        .update();
    ENGINE.job().ofInstance(wfInstanceKey).withType("sub_type").complete();

    // then
    final Record<JobBatchRecordValue> job = ENGINE.jobs().withType(helper.getJobType()).activate();
    final Map<String, Object> jobVariables =
        job.getValue().getJobs().iterator().next().getVariables();

    // when
    assertThat(jobVariables).containsOnly(Map.entry("key", 123));
  }

  private static void assertEventSubprocessLifecycle(final long processInstanceKey) {
    final List<Record<ProcessInstanceRecordValue>> events =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .filter(r -> r.getValue().getElementId().startsWith("event_sub_"))
            .limit(15)
            .asList();

    assertThat(events)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsExactly(
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATING, "event_sub_proc"),
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATED, "event_sub_proc"),
            tuple(ProcessInstanceIntent.ACTIVATE_ELEMENT, "event_sub_start"),
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATING, "event_sub_start"),
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATED, "event_sub_start"),
            tuple(ProcessInstanceIntent.COMPLETE_ELEMENT, "event_sub_start"),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETING, "event_sub_start"),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, "event_sub_start"),
            tuple(ProcessInstanceIntent.ACTIVATE_ELEMENT, "event_sub_end"),
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
            .getDeployedProcesses()
            .get(0);

    final long wfInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("key", 123))
            .create();
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(wfInstanceKey)
                .exists())
        .describedAs("Expected job to be created")
        .isTrue();

    triggerEventSubprocess.accept(wfInstanceKey);
    return wfInstanceKey;
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
}

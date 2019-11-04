/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.EmbeddedSubProcessBuilder;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.model.bpmn.builder.StartEventBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EventSubprocessTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameterized.Parameter public String testName;

  @Parameterized.Parameter(1)
  public Function<StartEventBuilder, StartEventBuilder> builder;

  @Parameterized.Parameter(2)
  public Consumer<Long> triggerEventSubprocess;

  private DeployedWorkflow currentWorkflow;

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
                          .withWorkflowInstanceKey(key)
                          .exists())
                  .describedAs("Expected timer to exist")
                  .isTrue();
              ENGINE.increaseTime(Duration.ofSeconds(60));
            })
      },
      {
        "message",
        eventSubprocess(s -> s.message(b -> b.name("msg").zeebeCorrelationKey("key"))),
        eventTrigger(
            key -> {
              RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED)
                  .withWorkflowInstanceKey(key)
                  .withMessageName("msg")
                  .await();
              ENGINE
                  .message()
                  .withName("msg")
                  .withCorrelationKey("123")
                  .withTimeToLive(0L)
                  .publish();
            })
      },
    };
  }

  private static Function<StartEventBuilder, StartEventBuilder> eventSubprocess(
      Function<StartEventBuilder, StartEventBuilder> consumer) {
    return consumer;
  }

  private static Consumer<Long> eventTrigger(Consumer<Long> eventTrigger) {
    return eventTrigger;
  }

  @Test
  public void shouldTriggerEventSubprocess() {
    // when
    final BpmnModelInstance model = eventSubprocModel(false, builder);
    final long wfInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    final Record<WorkflowInstanceRecordValue> eventOccurred =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED)
            .withWorkflowInstanceKey(wfInstanceKey)
            .getFirst();
    Assertions.assertThat(eventOccurred.getValue())
        .hasWorkflowKey(currentWorkflow.getWorkflowKey())
        .hasWorkflowInstanceKey(wfInstanceKey)
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasElementId("event_sub_start")
        .hasVersion(currentWorkflow.getVersion())
        .hasFlowScopeKey(wfInstanceKey);

    assertEventSubprocessLifecycle(wfInstanceKey);
  }

  @Test
  public void shouldNotInterruptParentProcess() {
    // when
    final BpmnModelInstance model = eventSubprocModel(false, builder);
    final long wfInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    assertEventSubprocessLifecycle(wfInstanceKey);
    ENGINE.job().ofInstance(wfInstanceKey).withType("type").complete();

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(wfInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.START_EVENT, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInterruptAndCompleteParent() {
    // given
    final BpmnModelInstance model = eventSubprocModel(true, builder);
    final long wfInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(wfInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.START_EVENT, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInterruptNestedSubprocess() {
    // given
    final BpmnModelInstance model = nestedInterruptingEventSubprocess(builder);
    final long wfInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    final Record<WorkflowInstanceRecordValue> subProcess =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETING)
            .withWorkflowInstanceKey(wfInstanceKey)
            .withElementId("sub_proc")
            .getFirst();

    final Record<WorkflowInstanceRecordValue> eventSubproc =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(wfInstanceKey)
            .withElementId("event_sub_proc")
            .getFirst();

    assertThat(eventSubproc.getValue().getFlowScopeKey()).isEqualTo(subProcess.getKey());
    assertThat(subProcess.getValue().getFlowScopeKey()).isEqualTo(wfInstanceKey);
    assertThat(subProcess.getSourceRecordPosition()).isEqualTo(eventSubproc.getPosition());
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_TERMINATED)
                .withWorkflowInstanceKey(wfInstanceKey)
                .withElementId("task")
                .getFirst())
        .isNotNull();
  }

  private static void assertEventSubprocessLifecycle(long workflowInstanceKey) {
    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .filter(r -> r.getValue().getElementId().startsWith("event_sub_"))
            .limit(13)
            .asList();

    assertThat(events)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsExactly(
            tuple(WorkflowInstanceIntent.EVENT_OCCURRED, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_proc"));
  }

  private long createInstanceAndTriggerEvent(BpmnModelInstance model) {
    currentWorkflow =
        ENGINE
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    final long wfInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("proc").withVariable("key", "123").create();
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withWorkflowInstanceKey(wfInstanceKey)
                .exists())
        .describedAs("Expected job to be created")
        .isTrue();

    triggerEventSubprocess.accept(wfInstanceKey);
    return wfInstanceKey;
  }

  private static BpmnModelInstance eventSubprocModel(
      final boolean interrupting,
      final Function<StartEventBuilder, StartEventBuilder> startBuilder) {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("proc");
    startBuilder
        .apply(
            builder
                .eventSubProcess("event_sub_proc")
                .startEvent("event_sub_start")
                .interrupting(interrupting))
        .endEvent("event_sub_end");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeTaskType("type"))
        .endEvent("end_proc")
        .done();
  }

  private static BpmnModelInstance nestedInterruptingEventSubprocess(
      Function<StartEventBuilder, StartEventBuilder> builder) {
    final EmbeddedSubProcessBuilder embeddedBuilder =
        Bpmn.createExecutableProcess("proc")
            .startEvent("proc_start")
            .subProcess("sub_proc")
            .embeddedSubProcess();
    builder
        .apply(
            embeddedBuilder
                .eventSubProcess("event_sub_proc")
                .startEvent("event_sub_start")
                .interrupting(true))
        .endEvent("event_sub_end");
    return embeddedBuilder
        .startEvent("sub_start")
        .serviceTask("task", t -> t.zeebeTaskType("type"))
        .endEvent("sub_end")
        .done();
  }
}

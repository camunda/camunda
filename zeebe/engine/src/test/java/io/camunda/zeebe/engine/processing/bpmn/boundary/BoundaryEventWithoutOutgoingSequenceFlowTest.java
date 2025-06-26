/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.boundary;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Parameterized runtime tests for boundary events without outgoing sequence flows. Interrupting
 * boundary event terminates the attached activity. Non-interrupting boundary events leave it
 * running, and we complete the job created by the service task to verify that the process instance
 * completes.
 */
@RunWith(Parameterized.class)
public final class BoundaryEventWithoutOutgoingSequenceFlowTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();
  private final String name;
  private final BoundaryScenario builder;
  private final BoundaryTrigger trigger;
  private final boolean shouldTerminate;
  private long instanceKey;

  public BoundaryEventWithoutOutgoingSequenceFlowTest(
      final String name,
      final BoundaryScenario builder,
      final BoundaryTrigger trigger,
      final boolean shouldTerminate) {
    this.name = name;
    this.builder = builder;
    this.trigger = trigger;
    this.shouldTerminate = shouldTerminate;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "message-int",
            (BoundaryScenario)
                b ->
                    b.serviceTask("element", t -> t.zeebeJobType("type"))
                        .boundaryEvent(
                            "boundary",
                            bb ->
                                bb.message(m -> m.name("MSG").zeebeCorrelationKeyExpression("k"))),
            (BoundaryTrigger)
                (engine, key) -> engine.message().withName("MSG").withCorrelationKey("v").publish(),
            true
          },
          {
            "message-non-int",
            (BoundaryScenario)
                b ->
                    b.serviceTask("element", t -> t.zeebeJobType("type"))
                        .boundaryEvent(
                            "boundary",
                            bb ->
                                bb.cancelActivity(false)
                                    .message(
                                        m -> m.name("MSG").zeebeCorrelationKeyExpression("k"))),
            (BoundaryTrigger)
                (engine, key) -> engine.message().withName("MSG").withCorrelationKey("v").publish(),
            false
          },
          {
            "timer-int",
            (BoundaryScenario)
                b ->
                    b.serviceTask("element", t -> t.zeebeJobType("type"))
                        .boundaryEvent("boundary", bb -> bb.timerWithDuration("PT1S")),
            (BoundaryTrigger) (engine, key) -> engine.increaseTime(Duration.ofSeconds(2)),
            true
          },
          {
            "timer-non-int",
            (BoundaryScenario)
                b ->
                    b.serviceTask("element", t -> t.zeebeJobType("type"))
                        .boundaryEvent(
                            "boundary", bb -> bb.cancelActivity(false).timerWithDuration("PT1S")),
            (BoundaryTrigger) (engine, key) -> engine.increaseTime(Duration.ofSeconds(2)),
            false
          },
          {
            "signal-int",
            (BoundaryScenario)
                b ->
                    b.serviceTask("element", t -> t.zeebeJobType("type"))
                        .boundaryEvent("boundary", bb -> bb.signal("SIG")),
            (BoundaryTrigger) (engine, key) -> engine.signal().withSignalName("SIG").broadcast(),
            true
          },
          {
            "signal-non-int",
            (BoundaryScenario)
                b ->
                    b.serviceTask("element", t -> t.zeebeJobType("type"))
                        .boundaryEvent("boundary", bb -> bb.cancelActivity(false).signal("SIG")),
            (BoundaryTrigger) (engine, key) -> engine.signal().withSignalName("SIG").broadcast(),
            false
          },
          {
            "escalation-int",
            (BoundaryScenario)
                b ->
                    b.subProcess(
                            "element",
                            sub ->
                                sub.embeddedSubProcess()
                                    .startEvent()
                                    .serviceTask("triggerTask", t -> t.zeebeJobType("triggerType"))
                                    .intermediateThrowEvent(
                                        "escalationThrow", e -> e.escalation("ESC"))
                                    .endEvent())
                        .boundaryEvent("boundary", bb -> bb.escalation("ESC"))
                        .moveToActivity("element"),
            (BoundaryTrigger)
                (engine, key) -> engine.job().ofInstance(key).withType("triggerType").complete(),
            true
          },
          {
            "escalation-non-int",
            (BoundaryScenario)
                b ->
                    b.subProcess(
                            "element",
                            sub ->
                                sub.embeddedSubProcess()
                                    .startEvent()
                                    .serviceTask("triggerTask", t -> t.zeebeJobType("triggerType"))
                                    .intermediateThrowEvent(
                                        "escalationThrow", e -> e.escalation("ESC"))
                                    .serviceTask("task", t -> t.zeebeJobType("type"))
                                    .endEvent())
                        .boundaryEvent("boundary", bb -> bb.cancelActivity(false).escalation("ESC"))
                        .moveToActivity("element"),
            (BoundaryTrigger)
                (engine, key) -> engine.job().ofInstance(key).withType("triggerType").complete(),
            false
          },
          {
            "error-int",
            (BoundaryScenario)
                b ->
                    b.subProcess(
                            "element",
                            sub ->
                                sub.embeddedSubProcess()
                                    .startEvent()
                                    .serviceTask("triggerTask", t -> t.zeebeJobType("triggerType"))
                                    .endEvent("end", e -> e.error("ERR")))
                        .boundaryEvent("boundary", bb -> bb.error("ERR"))
                        .moveToActivity("element"),
            (BoundaryTrigger)
                (engine, key) -> engine.job().ofInstance(key).withType("triggerType").complete(),
            true
          }
        });
  }

  @Before
  public void deployAndCreateInstance() {
    final BpmnModelInstance model = betweenStartAndEnd("process-" + name, builder);
    ENGINE.deployment().withXmlResource(model).deploy();

    instanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process-" + name).withVariable("k", "v").create();
  }

  @Test
  public void shouldTerminateOrKeepElementWithBoundaryAttached() {
    trigger.trigger(ENGINE, instanceKey);

    if (shouldTerminate) {
      final boolean terminated =
          RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
              .withProcessInstanceKey(instanceKey)
              .withElementId("element")
              .exists();
      assertThat(terminated).isTrue();
    } else {
      final boolean jobCreated =
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withProcessInstanceKey(instanceKey)
              .withType("type")
              .exists();
      assertThat(jobCreated).isTrue();

      // to verify completing the job and the process
      ENGINE.job().ofInstance(instanceKey).withType("type").complete();
    }

    final boolean instanceCompleted =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withElementType(BpmnElementType.PROCESS)
            .withProcessInstanceKey(instanceKey)
            .withElementId("process-" + name)
            .exists();

    assertThat(instanceCompleted).isTrue();
  }

  /** Builds the process between startEvent() → ... → endEvent().done() */
  private static BpmnModelInstance betweenStartAndEnd(
      final String processId, final BoundaryScenario between) {

    final AbstractFlowNodeBuilder<?, ?> start =
        Bpmn.createExecutableProcess(processId).startEvent();
    final AbstractFlowNodeBuilder<?, ?> afterActivity = between.apply(start);
    return afterActivity.endEvent().done();
  }

  /** Inserts serviceTask + boundaryEvent without outgoing flows. */
  @FunctionalInterface
  private interface BoundaryScenario {
    AbstractFlowNodeBuilder<?, ?> apply(AbstractFlowNodeBuilder<?, ?> builder);
  }

  /** Triggers the boundary event at runtime. */
  @FunctionalInterface
  private interface BoundaryTrigger {
    void trigger(EngineRule engine, long instanceKey);
  }
}

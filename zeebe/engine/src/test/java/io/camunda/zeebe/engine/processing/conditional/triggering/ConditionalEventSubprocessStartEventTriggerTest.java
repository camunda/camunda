/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional.triggering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.groups.Tuple;
import org.junit.Rule;
import org.junit.Test;

public class ConditionalEventSubprocessStartEventTriggerTest {
  private static final String TASK_ID = "task";
  private static final String CATCH_ID = "catchEvent";
  private static final String SUB_ID = "subProcess";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void
      shouldTriggerInterruptingEventSubprocessStartEventWhenParentVariableCreationInProcessScope() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID, b -> b.startEvent(CATCH_ID).condition("=x > 10").endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(CATCH_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(CATCH_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotTriggerEventSubprocessStartEventWhenConditionStaysFalseOnUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID, b -> b.startEvent(CATCH_ID).condition("=x > 10").endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when (x is created/updated but condition remains false)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 5)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(1))
        .isEmpty();
  }

  @Test
  public void
      shouldTriggerNonInterruptingEventSubprocessStartEventWhenParentVariableCreationInProcessScope() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b -> b.startEvent(CATCH_ID).interrupting(false).condition("=x > 10").endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .limit(1))
        .hasSize(0);

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(CATCH_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(CATCH_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void
      shouldNotTriggerNonInterruptingEventSubprocessStartEventWhenConditionStaysFalseOnUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b -> b.startEvent(CATCH_ID).interrupting(false).condition("=x > 10").endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when (x is created/updated but condition remains false)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 5)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerEventSubprocessStartEventOnTaskLocalVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b -> b.startEvent(CATCH_ID).interrupting(false).condition("=x > 10").endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final var elementInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when - (variable updated in task local scope)
    engine
        .variables()
        .ofScope(elementInstanceKey)
        .withDocument(Map.of("x", 20))
        .withLocalSemantic()
        .update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldAllowMultipleTriggersOnNonInterruptingEventSubprocessStartEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b -> b.startEvent(CATCH_ID).interrupting(false).condition("=x > 10").endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 12)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(2))
        .hasSize(2);

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ID)
                .limit(1))
        .isEmpty();
  }

  @Test
  public void
      shouldAllowMultipleTriggersOnNonInterruptingEventSubprocessStartEventAndStillCompleteTask() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b -> b.startEvent(CATCH_ID).interrupting(false).condition("=x > 10").endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 12)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(2))
        .hasSize(2);

    // and task can still complete afterward
    engine.job().ofInstance(processInstanceKey).withType("task").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ID)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldNotTriggerEventSubprocessStartEventWhenNonFilteredVariableChanges() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b ->
                        b.startEvent(CATCH_ID)
                            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when (only y changes; x unchanged – filter should skip evaluation)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("y", 11)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of("x"))
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(0);
  }

  @Test
  public void shouldTriggerOnlyMatchingEventSubprocessStartEventWhenMultipleVariableNameFilters() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    "sub_x",
                    b ->
                        b.startEvent("catch_x")
                            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                            .endEvent())
                .eventSubProcess(
                    "sub_y",
                    b ->
                        b.startEvent("catch_y")
                            .condition(c -> c.condition("=y > 10").zeebeVariableNames("y"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when (only x changes; y unchanged – filter should skip evaluation)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then  (only catch_x should trigger)
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("catch_x")
                .withCondition("=x > 10")
                .withVariableNames(List.of("x"))
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("catch_y")
                .withCondition("=y > 10")
                .withVariableNames(List.of("y"))
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(0);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple("catch_x", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("catch_x", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("sub_x", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("sub_x", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerEventSubprocessStartEventWithoutVariableNameFilterOnAnyVariableChange() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b -> b.startEvent(CATCH_ID).interrupting(false).condition("=x > 10").endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when (change variable y; since there is no filter, evaluation must run and trigger)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("y", 0)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(2))
        .hasSize(2);
  }

  @Test
  public void shouldTriggerEventSubprocessStartEventOnCreateEventWhenVariableEventsCreate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b ->
                        b.startEvent(CATCH_ID)
                            .condition(
                                c ->
                                    c.condition("=x > 10")
                                        .zeebeVariableNames("x")
                                        .zeebeVariableEvents("create"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of("x"))
                .withVariableEvents(List.of("create"))
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(CATCH_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(CATCH_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotTriggerEventSubprocessStartEventOnUpdateEventWhenVariableEventsCreateOnly() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b ->
                        b.startEvent(CATCH_ID)
                            .condition(
                                c ->
                                    c.condition("=x > 10")
                                        .zeebeVariableNames("x")
                                        .zeebeVariableEvents("create"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariable("x", 5).create();

    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of("x"))
                .withVariableEvents(List.of("create"))
                .limit(1))
        .hasSize(0);
  }

  @Test
  public void shouldTriggerEventSubprocessStartEventOnUpdateEventWhenVariableEventsUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b ->
                        b.startEvent(CATCH_ID)
                            .condition(
                                c ->
                                    c.condition("=x > 10")
                                        .zeebeVariableNames("x")
                                        .zeebeVariableEvents("update"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when - x is created with a value that already satisfies the condition
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then - no trigger yet (CREATE event ignored)
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .limit(1))
        .hasSize(0);

    // and when - x is updated again (UPDATE event) and still satisfies the condition
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 12)).update();

    // then - event subprocess start event should trigger now (on UPDATE)
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of("x"))
                .withVariableEvents(List.of("update"))
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(CATCH_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(CATCH_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void
      shouldTriggerEventSubprocessStartEventOnCreateAndUpdateWhenVariableEventsCreateAndUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b ->
                        b.startEvent(CATCH_ID)
                            .interrupting(false)
                            .condition(
                                c ->
                                    c.condition("=x > 10")
                                        .zeebeVariableNames("x")
                                        .zeebeVariableEvents("create,update"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when - x created with value > 10 (CREATE)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    // and - x updated again (UPDATE)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 12)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of("x"))
                .withVariableEvents(List.of("create", "update"))
                .limit(2))
        .hasSize(2);
  }

  @Test
  public void shouldTriggerMultipleStartEventsOnNonInterruptingEventWhenConditionsSatisfied() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    "sub_a",
                    b ->
                        b.startEvent("catch_a")
                            .interrupting(false)
                            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                            .endEvent())
                .eventSubProcess(
                    "sub_b",
                    b ->
                        b.startEvent("catch_b")
                            .interrupting(false)
                            .condition(c -> c.condition("=x > 5").zeebeVariableNames("x"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withVariableNames(List.of("x"))
                .withVariableEvents(List.of())
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ConditionalSubscriptionRecordValue::getCatchEventId,
            ConditionalSubscriptionRecordValue::getCondition)
        .containsExactlyInAnyOrder(
            Tuple.tuple("catch_a", "=x > 10"), Tuple.tuple("catch_b", "=x > 5"))
        .hasSize(2);
  }

  @Test
  public void
      shouldTriggerInnerEventSubprocessStartEventWhenOuterConditionFalseAndInnerConditionTrue() {
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    SUB_ID,
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                            .endEvent()
                            .moveToSubProcess(SUB_ID)
                            .eventSubProcess(
                                "inner",
                                b ->
                                    b.startEvent("inner_catch")
                                        .condition(
                                            c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                        .endEvent()))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    "outer",
                    b ->
                        b.startEvent("outer_catch")
                            .condition(c -> c.condition("=x > 100").zeebeVariableNames("x"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long subprocessInstanceKey = awaitElementInstance(processInstanceKey, SUB_ID);

    // when - variable in subprocess scope makes only inner condition true
    engine.variables().ofScope(subprocessInstanceKey).withDocument(Map.of("x", 11)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("outer_catch")
                .limit(1))
        .isEmpty();

    // and inner event subprocess start event is triggered
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("inner_catch")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple("inner_catch", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("inner_catch", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("inner", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("inner", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerOuterEventSubprocessStartEventWhenConditionTrueOnBothLevels() {
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    SUB_ID,
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                            .endEvent()
                            .moveToSubProcess(SUB_ID)
                            .eventSubProcess(
                                "inner",
                                b ->
                                    b.startEvent("inner_catch")
                                        .condition(
                                            c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                        .endEvent()))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    "outer",
                    b ->
                        b.startEvent("outer_catch")
                            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("inner_catch")
                .limit(1))
        .isEmpty();

    // and outer event subprocess start event is triggered
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("outer_catch")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple("outer_catch", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("outer_catch", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("outer", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("outer", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void
      shouldTriggerInnerAndOuterNonInterruptingEventSubprocessStartEventsWhenConditionTrueOnBothLevels() {
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    SUB_ID,
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                            .endEvent()
                            .moveToSubProcess(SUB_ID)
                            .eventSubProcess(
                                "inner",
                                b ->
                                    b.startEvent("inner_catch")
                                        .interrupting(false)
                                        .condition(
                                            c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                        .endEvent()))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    "outer",
                    b ->
                        b.startEvent("outer_catch")
                            .interrupting(false)
                            .condition(c -> c.condition("=x > 5").zeebeVariableNames("x"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ConditionalSubscriptionRecordValue::getCatchEventId,
            ConditionalSubscriptionRecordValue::getCondition)
        .containsExactly(tuple("outer_catch", "=x > 5"), tuple("inner_catch", "=x > 10"))
        .hasSize(2);
  }

  @Test
  public void
      shouldTriggerEventSubprocessStartEventOnlyForSubprocessInstanceWhereVariableChanged() {
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway("fork")
                .subProcess(
                    "sub1",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("sub1Task", b -> b.zeebeJobType("task"))
                            .endEvent()
                            .moveToSubProcess("sub1")
                            .eventSubProcess(
                                "eventSub1",
                                b ->
                                    b.startEvent("eventSub1Start")
                                        .condition(
                                            c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                        .endEvent()))
                .parallelGateway("join")
                .endEvent()
                .moveToNode("fork")
                .subProcess(
                    "sub2",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("sub2Task", b -> b.zeebeJobType("task"))
                            .endEvent()
                            .moveToSubProcess("sub2")
                            .eventSubProcess(
                                "eventSub2",
                                b ->
                                    b.startEvent("eventSub2Start")
                                        .condition(
                                            c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                        .endEvent()))
                .connectTo("join")
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long sub1ElementInstanceKey = awaitElementInstance(processInstanceKey, "sub1");
    awaitElementInstance(processInstanceKey, "sub2");

    // when - variable changed only in subprocess instance 1 scope
    engine
        .variables()
        .ofScope(sub1ElementInstanceKey)
        .withDocument(Map.of("x", 20))
        .withLocalSemantic()
        .update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then - eventSub1Start  triggers
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(sub1ElementInstanceKey)
                .withCatchEventId("eventSub1Start")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    // and eventSub2Start does NOT trigger (scoped evaluation)
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("eventSub2Start")
                .limit(1))
        .isEmpty();
  }

  @Test
  public void
      shouldAllowMultipleTriggersOnNonInterruptingEventSubprocessStartEventWhileMiRunning() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .subProcess(
                    "subMi",
                    sub ->
                        sub.multiInstance(
                                b ->
                                    b.zeebeInputCollectionExpression("[1,2,3]")
                                        .zeebeInputElement("item"))
                            .embeddedSubProcess()
                            .startEvent()
                            .serviceTask(TASK_ID, t -> t.zeebeJobType("inner"))
                            .endEvent()
                            .subProcessDone())
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    "eventSubProcess",
                    b ->
                        b.startEvent(CATCH_ID)
                            .interrupting(false)
                            .condition(c -> c.condition("=x > 0").zeebeVariableNames("x"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // collect all inner jobs
    final var innerJobs =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ID)
            .limit(3);

    // when - each child completion writes x > 0 while MI still running
    final AtomicInteger i = new AtomicInteger(1);
    innerJobs.forEach(
        job -> {
          engine.job().withKey(job.getKey()).withVariables(Map.of("x", i)).complete();
          i.getAndIncrement();
        });

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 0")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(3))
        .hasSize(3);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .limitToProcessInstanceCompleted()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("subMi")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerEventSubprocessStartEventOnlyForMiChildWhereLocalVariableChanges() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .subProcess(
                    "subMi",
                    sub ->
                        sub.multiInstance(
                                b ->
                                    b.zeebeInputCollectionExpression("[1,2,3]")
                                        .zeebeInputElement("item"))
                            .embeddedSubProcess()
                            .startEvent()
                            .serviceTask(TASK_ID, t -> t.zeebeJobType("inner"))
                            .endEvent()
                            .moveToSubProcess("subMi")
                            .eventSubProcess(
                                "eventSubProcess",
                                b ->
                                    b.startEvent(CATCH_ID)
                                        .condition(
                                            c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                        .endEvent()))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // collect sub-process instances
    final var subElementInstances =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .withElementId("subMi")
            .limit(3)
            .toList();

    final long childScopeToChange = subElementInstances.getFirst().getKey();

    // when - variable changed only in one child's local scope
    engine
        .variables()
        .ofScope(childScopeToChange)
        .withDocument(Map.of("x", 20))
        .withLocalSemantic()
        .update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withScopeKey(childScopeToChange)
                .withCatchEventId(CATCH_ID))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerInnerEventSubprocessStartEventsWhenVariableChangedAtProcessScopeInMi() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .subProcess(
                    "subMi",
                    sub ->
                        sub.multiInstance(
                                b ->
                                    b.zeebeInputCollectionExpression("[1,2,3]")
                                        .zeebeInputElement("item"))
                            .embeddedSubProcess()
                            .startEvent()
                            .serviceTask(TASK_ID, t -> t.zeebeJobType("inner"))
                            .endEvent()
                            .moveToSubProcess("subMi")
                            .eventSubProcess(
                                "eventSubProcess",
                                b ->
                                    b.startEvent(CATCH_ID)
                                        .condition(
                                            c -> c.condition("=x > 10").zeebeVariableNames("x"))
                                        .endEvent()))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // ensure MI inner tasks are active
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ID)
                .limit(3))
        .hasSize(3);

    // when - variable x is updated at process scope (visible to all children)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    final var subProcessInstances =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .withElementId("subMi")
            .limit(3)
            .toList();

    final var childScopes = subProcessInstances.stream().map(Record::getKey).toArray(Long[]::new);

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .limit(3))
        .hasSize(3)
        .extracting(Record::getValue)
        .extracting(ConditionalSubscriptionRecordValue::getScopeKey)
        .containsExactlyInAnyOrder(childScopes);
  }

  @Test
  public void shouldTriggerEventSubprocessStartEventWhenAggregatedVariablesMakeConditionTrue() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .subProcess(
                    "subMi",
                    sub ->
                        sub.multiInstance(
                                b ->
                                    b.zeebeInputCollectionExpression("[1,2,3]")
                                        .zeebeInputElement("item"))
                            .embeddedSubProcess()
                            .startEvent()
                            .serviceTask("inner", t -> t.zeebeJobType("inner"))
                            .endEvent())
                .serviceTask(TASK_ID, t -> t.zeebeJobType("outer"))
                .moveToProcess(processId)
                .eventSubProcess(
                    "event",
                    b ->
                        b.startEvent(CATCH_ID)
                            .condition(c -> c.condition("=sum > 10").zeebeVariableNames("sum"))
                            .endEvent())
                .done())
        .deploy();

    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariable("sum", 0).create();

    // three inner jobs for MI
    final var innerJobs =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("inner")
            .limit(3)
            .toList();

    // when - each child completion "contributes" to sum; last one pushes it over threshold
    engine.job().withKey(innerJobs.get(0).getKey()).withVariables(Map.of("sum", 5)).complete();
    engine.job().withKey(innerJobs.get(1).getKey()).withVariables(Map.of("sum", 9)).complete();
    engine.job().withKey(innerJobs.get(2).getKey()).withVariables(Map.of("sum", 15)).complete();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=sum > 10")
                .withVariableNames("sum")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerEventSubprocessStartEventWhenChildProcessInstanceVariablePropagation() {
    // given
    final String parentProcessId = "parentProcessId";
    final String childProcessId = "childProcessId";

    final var child =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .endEvent()
            .zeebeOutputExpression("= 20", "x")
            .done();

    // callActivity allows to propagate all child variables
    final var parent =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "callActivity",
                c -> c.zeebeProcessId(childProcessId).zeebePropagateAllChildVariables(true))
            .endEvent()
            .moveToProcess(parentProcessId)
            .eventSubProcess(
                SUB_ID,
                b -> b.startEvent(CATCH_ID).interrupting(false).condition("=x > 10").endEvent())
            .done();

    engine.deployment().withXmlResource(child).withXmlResource(parent).deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void
      shouldTriggerEventSubprocessStartEventWhenVariableSetAtEndPhaseWhileSubprocessScopeIsActive() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(TASK_ID, b -> b.zeebeJobType("task"))
                .endEvent("outerEnd")
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID, b -> b.startEvent(CATCH_ID).condition("=x > 10").endEvent("subEnd"))
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    engine.job().ofInstance(processInstanceKey).withType("task").withVariable("x", 20).complete();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    // process completes via event subprocess
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(TASK_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(CATCH_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(CATCH_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("subEnd", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("subEnd", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(SUB_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    // and not via outer end event
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("outerEnd")
                .limit(1))
        .isEmpty();
  }

  @Test
  public void
      shouldTriggerEventSubprocessStartEventWhenConditionMatchesAfterPartialVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess(
                    SUB_ID,
                    b ->
                        b.startEvent(CATCH_ID)
                            .condition(c -> c.condition("=x + y > 10").zeebeVariableNames("x,y"))
                            .endEvent("subEnd"))
                .done())
        .deploy();

    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 6)).create();

    // when
    engine
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("y", 5))
        .withLocalSemantic()
        .update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x + y > 10")
                .withVariableNames("x", "y")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ID)
                .limit(1))
        .hasSize(1);
  }

  private long awaitElementInstance(final long processInstanceKey, final String elementId) {
    final Record<ProcessInstanceRecordValue> activated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(elementId)
            .getFirst();

    return activated.getKey();
  }
}

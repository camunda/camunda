/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional.triggering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;

public final class ConditionalBoundaryEventTriggerTest {

  private static final String TASK_ID = "task";
  private static final String SIBLING_TASK_ID = "siblingTask";
  private static final String SUBPROCESS_ID = "subprocess";
  private static final String INNER_TASK_ID = "innerTask";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldTriggerInterruptingBoundaryOnLocalVariableCreationInActivityScope() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(
                TASK_ID,
                t -> t.zeebeJobType("task").zeebeOutputExpression("=x", "x")) // keep simple
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when (variable created in task scope, condition becomes true)
    engine
        .variables()
        .ofScope(taskInstanceKey)
        .withDocument(Map.of("x", 20))
        .withLocalSemantic()
        .update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
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

  @Test
  public void shouldNotTriggerInterruptingBoundaryWhenConditionStaysFalseOnUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when (x is created/updated but condition remains false)
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 5)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("boundary")
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldTriggerNonInterruptingBoundaryOnLocalVariableCreationInActivityScope() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(false)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when (x created in task scope, condition becomes true)
    engine
        .variables()
        .ofScope(taskInstanceKey)
        .withDocument(Map.of("x", 42))
        .withLocalSemantic()
        .update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then (boundary triggers once, activity stays active => still ELEMENT_ACTIVATED)
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

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
  public void shouldNotTriggerNonInterruptingBoundaryWhenConditionStaysFalseOnUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(false)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when (x is created/updated but condition remains false)
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 5)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("boundary")
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldAllowMultipleTriggersOnNonInterruptingBoundaryWhileActivityActive() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(false)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when (x updated twice, condition stays true both times)
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 11)).update();
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 12)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then (non-interrupting boundary may trigger again)
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(2))
        .hasSize(2);

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ID)
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerBoundaryIfActivityCompletesBeforeConditionBecomesTrue() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .userTask("userTask")
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when (complete the job without ever making condition true)
    engine.job().ofInstance(processInstanceKey).withType("task").complete();

    awaitElementInstance(processInstanceKey, "userTask");
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 42)).update();

    // then (boundary never triggers)
    engine.signal().withSignalName("testSpeedUp").broadcast();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("boundary")
                .limit(1))
        .isEmpty();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ID)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerBoundaryOnParentProcessVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    awaitElementInstance(processInstanceKey, TASK_ID);

    // when (variable updated in process scope, visible to boundary)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 99)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldNotTriggerBoundaryOnSiblingTaskLocalVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .parallelGateway("fork")
            .serviceTask(SIBLING_TASK_ID, t -> t.zeebeJobType("sibling"))
            .parallelGateway("join")
            .endEvent("end")
            .moveToNode("fork")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToNode(TASK_ID)
            .connectTo("join")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long siblingTaskInstanceKey = awaitElementInstance(processInstanceKey, SIBLING_TASK_ID);

    // when (variable updated in sibling task local scope)
    engine
        .variables()
        .ofScope(siblingTaskInstanceKey)
        .withDocument(Map.of("x", 100))
        .withLocalSemantic()
        .update();

    // then (boundary on TASK_ID must not trigger)
    engine.signal().withSignalName("testSpeedUp").broadcast();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("boundary")
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldTriggerBoundaryOnSubprocessScopeVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .subProcess(
                SUBPROCESS_ID,
                sub ->
                    sub.embeddedSubProcess()
                        .startEvent("subStart")
                        .serviceTask(INNER_TASK_ID, t -> t.zeebeJobType("inner"))
                        .endEvent("subEnd"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(SUBPROCESS_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long subprocessInstanceKey = awaitElementInstance(processInstanceKey, SUBPROCESS_ID);

    // when (variable updated in subprocess scope which is scope of boundary)
    engine
        .variables()
        .ofScope(subprocessInstanceKey)
        .withDocument(Map.of("x", 11))
        .withLocalSemantic()
        .update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(SUBPROCESS_ID)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerBoundaryOnSubprocessWhenParentProcessVariableVisible() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .subProcess(
                SUBPROCESS_ID,
                sub ->
                    sub.embeddedSubProcess()
                        .startEvent("subStart")
                        .serviceTask(INNER_TASK_ID, t -> t.zeebeJobType("inner"))
                        .endEvent("subEnd"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(SUBPROCESS_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    awaitElementInstance(processInstanceKey, SUBPROCESS_ID);

    // when (update variable in outer process scope – visible to subprocess)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 123)).update();

    // then (boundary attached to subprocess should trigger)
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(SUBPROCESS_ID)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldNotTriggerBoundaryWhenNonFilteredVariableChanges() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when (only y changes; x unchanged – filter should skip evaluation)
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("y", 99)).update();

    // then
    engine.signal().withSignalName("testSpeedUp").broadcast();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("boundary")
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldTriggerOnlyMatchingBoundaryWhenMultipleVariableNameFilters() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            // boundary filtered on x
            .boundaryEvent("boundary_x")
            .cancelActivity(false)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundaryX")
            .moveToActivity(TASK_ID)
            // boundary filtered on y
            .boundaryEvent("boundary_y")
            .cancelActivity(false)
            .condition(c -> c.condition("=y > 10").zeebeVariableNames("y"))
            .endEvent("afterBoundaryY")
            .moveToActivity(TASK_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when (only x changes)
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 11)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then (only boundary_x should trigger)
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary_x")
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("boundary_y")
                .withCondition("=y > 10")
                .withVariableNames("y")
                .withVariableEvents(List.of())
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldTriggerBoundaryWithoutVariableNameFilterOnAnyVariableChange() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(false)
            // no zeebeVariableNames filter – any change should trigger evaluation
            .condition(c -> c.condition("=x > 10"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when (change variable y; since there is no filter, evaluation must run and trigger)
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 11)).update();
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("y", 0)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .limit(2))
        .hasSize(2);
  }

  @Test
  public void shouldTriggerBoundaryOnCreateEventWhenVariableEventsCreate() {
    // given
    final String processId = helper.getBpmnProcessId();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(
                c -> c.condition("=x > 10").zeebeVariableNames("x").zeebeVariableEvents("create"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when - x is created in task scope, and condition becomes true
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 11)).update();

    // then - boundary triggers (create event)
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of("create"))
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldNotTriggerBoundaryOnUpdateEventWhenVariableEventsCreateOnly() {
    // given
    final String processId = helper.getBpmnProcessId();

    // boundary listens only to "create"
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(
                c -> c.condition("=x > 10").zeebeVariableNames("x").zeebeVariableEvents("create"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            // create x in process scope BEFORE task is active, so change at task scope is UPDATE
            .withVariable("x", 1)
            .create();

    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when - update x in task scope so that condition would be true (UPDATE event)
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 11)).update();

    // then - boundary must NOT trigger because it only listens to CREATE
    engine.signal().withSignalName("testSpeedUp").broadcast();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("boundary")
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldTriggerBoundaryOnlyOnUpdateWhenVariableEventsUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();

    // boundary listens only to "update"
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(
                c -> c.condition("=x > 10").zeebeVariableNames("x").zeebeVariableEvents("update"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when - x is created with a value that already satisfies the condition
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 11)).update();

    // then - no trigger yet (CREATE event ignored)
    engine.signal().withSignalName("testSpeedUp").broadcast();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("boundary")
                .limit(1))
        .isEmpty();

    // and when - x is updated again (UPDATE event) and still satisfies the condition
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 12)).update();

    // then - boundary should trigger now (on UPDATE)
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of("update"))
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerBoundaryOnCreateAndUpdateWhenVariableEventsCreateAndUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();

    // boundary listens to both "create" and "update"
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(false)
            .condition(
                c ->
                    c.condition("=x > 10")
                        .zeebeVariableNames("x")
                        .zeebeVariableEvents("create,update"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when - x created with value > 10 (CREATE)
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 11)).update();

    // and then - x updated again (UPDATE)
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 12)).update();

    // then - boundary should trigger twice (once for CREATE, once for UPDATE)
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of("create", "update"))
                .limit(2))
        .hasSize(2);
  }

  @Test
  public void shouldInterruptSubprocessAndChildrenOnInterruptingBoundary() {
    // given
    final String processId = helper.getBpmnProcessId();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                SUBPROCESS_ID,
                sub ->
                    sub.embeddedSubProcess()
                        .startEvent("subStart")
                        .serviceTask(INNER_TASK_ID, t -> t.zeebeJobType("inner"))
                        .endEvent("subEnd"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(SUBPROCESS_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long subprocessInstanceKey = awaitElementInstance(processInstanceKey, SUBPROCESS_ID);
    awaitElementInstance(processInstanceKey, INNER_TASK_ID);

    // when - variable change makes condition true (update in subprocess scope)
    engine.variables().ofScope(subprocessInstanceKey).withDocument(Map.of("x", 11)).update();

    // then - boundary triggers
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    // subprocess and its inner task are terminated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(INNER_TASK_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(SUBPROCESS_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldAllowMultipleTriggersOnNonInterruptingBoundaryAndStillCompleteTask() {
    // given
    final String processId = helper.getBpmnProcessId();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(false)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when - condition becomes true multiple times while task is active
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 11)).update();
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 12)).update();

    // then - boundary may trigger twice
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(2))
        .hasSize(2);

    // and task can still complete afterward
    engine.job().ofInstance(processInstanceKey).withType("task").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerBothBoundariesWhenBothConditionsSatisfiedAndBothNonInterrupting() {
    // given
    final String processId = helper.getBpmnProcessId();

    // both non-interrupting; both conditions depend on x but with different thresholds
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundaryA")
            .cancelActivity(false)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundaryA")
            .moveToActivity(TASK_ID)
            .boundaryEvent("boundaryB")
            .cancelActivity(false)
            .condition(c -> c.condition("=x > 5").zeebeVariableNames("x"))
            .endEvent("afterBoundaryB")
            .moveToActivity(TASK_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when - update x so that both conditions are true
    engine.variables().ofScope(taskInstanceKey).withDocument(Map.of("x", 11)).update();

    // then - both non-interrupting boundaries can trigger
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ConditionalSubscriptionRecordValue::getCatchEventId,
            ConditionalSubscriptionRecordValue::getCondition)
        .containsExactlyInAnyOrder(tuple("boundaryA", "=x > 10"), tuple("boundaryB", "=x > 5"))
        .hasSize(2);
  }

  @Test
  public void shouldTriggerInnerBoundaryWhenOuterConditionFalseAndInnerConditionTrue() {
    // given
    final String processId = helper.getBpmnProcessId();

    // outer boundary condition: x > 100 (false)
    // inner boundary condition: x > 10 (true)
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .subProcess(
                SUBPROCESS_ID,
                sub ->
                    sub.embeddedSubProcess()
                        .startEvent("subStart")
                        .serviceTask(INNER_TASK_ID, t -> t.zeebeJobType("inner"))
                        .boundaryEvent("innerBoundary")
                        .cancelActivity(true)
                        .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                        .endEvent("afterInnerBoundary")
                        .moveToActivity(INNER_TASK_ID)
                        .endEvent("innerEnd"))
            .boundaryEvent("outerBoundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 100").zeebeVariableNames("x"))
            .endEvent("afterOuterBoundary")
            .moveToActivity(SUBPROCESS_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long subprocessInstanceKey = awaitElementInstance(piKey, SUBPROCESS_ID);
    awaitElementInstance(piKey, INNER_TASK_ID);

    // when - variable in subprocess scope makes only inner condition true
    engine.variables().ofScope(subprocessInstanceKey).withDocument(Map.of("x", 11)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then - outer boundary not triggered (condition false)
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("outerBoundary")
                .limit(1))
        .isEmpty();

    // and inner boundary is triggered after descending into nested scopes
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withCatchEventId("innerBoundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piKey)
                .withElementId(SUBPROCESS_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piKey)
                .withElementId(processId)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerOnlyOuterInterruptingBoundaryWhenConditionTrueOnBothLevels() {
    // given
    final String processId = helper.getBpmnProcessId();

    // both boundaries use same condition x > 10; outer is interrupting
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .subProcess(
                SUBPROCESS_ID,
                sub ->
                    sub.embeddedSubProcess()
                        .startEvent("subStart")
                        .serviceTask(INNER_TASK_ID, t -> t.zeebeJobType("inner"))
                        .boundaryEvent("innerBoundary")
                        .cancelActivity(true)
                        .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                        .endEvent("afterInnerBoundary")
                        .moveToActivity(INNER_TASK_ID)
                        .endEvent("innerEnd"))
            .boundaryEvent("outerBoundary")
            .cancelActivity(true) // interrupting
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterOuterBoundary")
            .moveToActivity(SUBPROCESS_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long subprocessInstanceKey = awaitElementInstance(piKey, SUBPROCESS_ID);
    awaitElementInstance(piKey, INNER_TASK_ID);

    // when - variable in subprocess scope satisfies condition at both levels
    engine.variables().ofScope(subprocessInstanceKey).withDocument(Map.of("x", 11)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then - outer interrupting boundary triggers
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withCatchEventId("outerBoundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    // then - inner boundary not triggered (never evaluated)
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("innerBoundary")
                .limit(1))
        .isEmpty();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piKey)
                .withElementId(SUBPROCESS_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piKey)
                .withElementId(processId)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerOuterAndInnerNonInterruptingBoundariesWhenConditionTrueOnBothLevels() {
    // given
    final String processId = helper.getBpmnProcessId();

    // both boundaries non-interrupting: x > 10
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .subProcess(
                SUBPROCESS_ID,
                sub ->
                    sub.embeddedSubProcess()
                        .startEvent("subStart")
                        .serviceTask(INNER_TASK_ID, t -> t.zeebeJobType("inner"))
                        .boundaryEvent("innerBoundary")
                        .cancelActivity(false)
                        .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                        .endEvent("afterInnerBoundary")
                        .moveToActivity(INNER_TASK_ID)
                        .endEvent("innerEnd"))
            .boundaryEvent("outerBoundary")
            .cancelActivity(false)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterOuterBoundary")
            .moveToActivity(SUBPROCESS_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long subprocessInstanceKey = awaitElementInstance(piKey, SUBPROCESS_ID);
    awaitElementInstance(piKey, INNER_TASK_ID);

    // when - variable in subprocess scope satisfies condition at both levels
    engine.variables().ofScope(subprocessInstanceKey).withDocument(Map.of("x", 11)).update();

    // then - outer non-interrupting boundary triggers
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ConditionalSubscriptionRecordValue::getCatchEventId,
            ConditionalSubscriptionRecordValue::getCondition)
        .containsExactlyInAnyOrder(
            tuple("innerBoundary", "=x > 10"), tuple("outerBoundary", "=x > 10"))
        .hasSize(2);
  }

  @Test
  public void shouldTriggerBoundaryOnlyForSubprocessInstanceWhereVariableChanged() {
    // given
    final String processId = helper.getBpmnProcessId();

    // Two parallel subprocesses of the same logical shape, each with its own boundary
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .parallelGateway("fork")
            .subProcess(
                "sub1",
                sub ->
                    sub.embeddedSubProcess()
                        .startEvent("sub1Start")
                        .serviceTask("inner1", t -> t.zeebeJobType("inner1"))
                        .endEvent("sub1End"))
            .boundaryEvent("boundary1")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary1")
            .moveToNode("sub1")
            .parallelGateway("join")
            .endEvent()
            .moveToNode("fork")
            .subProcess(
                "sub2",
                sub ->
                    sub.embeddedSubProcess()
                        .startEvent("sub2Start")
                        .serviceTask("inner2", t -> t.zeebeJobType("inner2"))
                        .endEvent("sub2End"))
            .boundaryEvent("boundary2")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary2")
            .moveToNode("sub2")
            .connectTo("join")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long sub1InstanceKey = awaitElementInstance(piKey, "sub1");
    awaitElementInstance(piKey, "sub2");

    // when - variable changed only in subprocess instance 1 scope
    engine
        .variables()
        .ofScope(sub1InstanceKey)
        .withDocument(Map.of("x", 11))
        .withLocalSemantic()
        .update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then - boundary1 triggers
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withCatchEventId("boundary1")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    // and boundary2 does NOT trigger (scoped evaluation)
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("boundary2")
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldRejectTriggerBoundaryFromJobCompletionVariablesInSameScope() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String jobType = "worker-type";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType(jobType))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var jobCreated =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(piKey)
            .withElementId(TASK_ID)
            .getFirst();

    // when - job completion writes x=11 into same scope as service task
    engine.job().withKey(jobCreated.getKey()).withVariables(Map.of("x", 11)).complete();

    // then
    engine.signal().withSignalName("testSpeedUp").broadcast();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("boundary")
                .limit(1))
        .isEmpty();

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    // service task scope is already left, so subscription DELETED is already applied to the state
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.TRIGGER)
                .onlyCommandRejections()
                .withProcessInstanceKey(piKey)
                .withCatchEventId("boundary")
                .limit(1)
                .getFirst())
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to trigger condition subscription with key '%d', but no such"
                    + " subscription was found for process instance with key '%d' and catch"
                    + " event id '%s'.",
                subscriptionKey, piKey, "boundary"));
  }

  @Test
  public void shouldTriggerOuterAndInnerSubprocessBoundaryOnMessageCorrelationInSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String messageName = "innerMsg";
    final String correlationKey = "sub-1";

    // Subprocess contains a message catch; outer boundary and future inner boundary
    // both "see" x, but only outer is active at correlation time.
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .subProcess(
                SUBPROCESS_ID,
                sub ->
                    sub.embeddedSubProcess()
                        .startEvent("subStart")
                        .intermediateCatchEvent("subMsg")
                        .message(
                            m ->
                                m.name(messageName).zeebeCorrelationKeyExpression("correlationKey"))
                        .userTask(INNER_TASK_ID)
                        .boundaryEvent("innerBoundary")
                        .cancelActivity(true)
                        .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                        .endEvent("afterInnerBoundary")
                        .moveToActivity(INNER_TASK_ID)
                        .endEvent("subEnd"))
            .boundaryEvent("outerBoundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterOuterBoundary")
            .moveToActivity(SUBPROCESS_ID)
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long piKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("correlationKey", correlationKey))
            .create();

    // wait until inner message subscription is opened in the subprocess
    RecordingExporter.messageSubscriptionRecords()
        .withProcessInstanceKey(piKey)
        .withMessageName(messageName)
        .getFirst();

    awaitElementInstance(piKey, SUBPROCESS_ID);

    // when - correlate message, changing x in subprocess scope
    engine
        .message()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withVariables(Map.of("x", 11))
        .publish();

    // then - outer subprocess boundary should trigger first (top-down evaluation)
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withCatchEventId("outerBoundary")
                .limit(1))
        .hasSize(1);

    // since activate element is written before element completion, inner boundary also triggered
    // however, this has no affect since out scope will be terminated first
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withCatchEventId("innerBoundary")
                .limit(1))
        .hasSize(1);

    // assert process completed
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piKey)
                .withElementId(processId)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isEqualTo(true);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(piKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(ProcessInstanceRecordValue::getElementId)
        .containsExactly(INNER_TASK_ID, SUBPROCESS_ID)
        .hasSize(2);
  }

  @Test
  public void shouldTriggerInterruptingBoundaryOnMiBodyWhenChildCompletionChangesVariable() {
    // given
    final String processId = helper.getBpmnProcessId();

    // Multi-instance subprocess with a boundary on the MI activity (sub1)
    final BpmnModelInstance process =
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
                        .startEvent("subStart")
                        .serviceTask("inner", t -> t.zeebeJobType("inner"))
                        .endEvent("subEnd")
                        .subProcessDone())
            .boundaryEvent("miBoundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity("subMi")
            .endEvent("afterMi")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // wait for at least one inner job to be created
    final var firstJob =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(piKey)
            .withElementId("inner")
            .getFirst();

    // when - completing first child instance job writes x > 0
    engine.job().withKey(firstJob.getKey()).withVariables(Map.of("x", 11)).complete();

    // then - MI boundary should trigger and interrupt the whole MI activity
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withCatchEventId("miBoundary")
                .limit(1))
        .hasSize(1);

    // and the multi-instance subprocess is terminated
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piKey)
                .withElementId("subMi")
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldAllowMultipleTriggersOnNonInterruptingMiBodyBoundaryWhileMiRunning() {
    // given
    final String processId = helper.getBpmnProcessId();

    // Non-interrupting boundary on MI subprocess
    final BpmnModelInstance process =
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
                        .startEvent("subStart")
                        .serviceTask("inner", t -> t.zeebeJobType("inner"))
                        .endEvent("subEnd")
                        .subProcessDone())
            .boundaryEvent("miBoundary")
            .cancelActivity(false)
            .condition(c -> c.condition("=x > 0").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity("subMi")
            .endEvent("afterMi")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // collect all inner jobs
    final var innerJobs =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(piKey)
            .withElementId("inner")
            .limit(3);

    // when - each child completion writes x > 0 while MI still running
    final AtomicInteger i = new AtomicInteger(1);
    innerJobs.forEach(
        job -> {
          engine.job().withKey(job.getKey()).withVariables(Map.of("x", i)).complete();
          i.getAndIncrement();
        });

    // then - non-interrupting boundary may trigger once per completion
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withCatchEventId("miBoundary")
                .limit(3))
        .hasSize(3);

    // and MI body can still complete normally
    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(piKey)
        .withElementId("subMi")
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .getFirst();
  }

  @Test
  public void shouldTriggerBoundaryOnlyForMiChildWhereLocalVariableChanges() {
    // given
    final String processId = helper.getBpmnProcessId();

    // MI subprocess; inner service task has a boundary
    final BpmnModelInstance process =
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
                        .startEvent("subStart")
                        .serviceTask(INNER_TASK_ID, t -> t.zeebeJobType("inner"))
                        .boundaryEvent("innerBoundary")
                        .cancelActivity(true)
                        .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                        .endEvent("afterInnerBoundary")
                        .moveToActivity(INNER_TASK_ID)
                        .endEvent("subEnd")
                        .subProcessDone())
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // collect all inner task instances
    final var innerInstances =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(piKey)
            .withElementId(INNER_TASK_ID)
            .limit(3)
            .toList();

    final long childScopeToChange = innerInstances.getFirst().getKey();

    // when - variable changed only in one child's local scope
    engine
        .variables()
        .ofScope(childScopeToChange)
        .withDocument(Map.of("x", 11))
        .withLocalSemantic()
        .update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then - boundary triggers only once (for that child)
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withScopeKey(childScopeToChange)
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("innerBoundary"))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerInnerBoundariesWhenVariableChangedAtProcessScopeInMi() {
    // given
    final String processId = helper.getBpmnProcessId();

    // MI subprocess; inner service task has a boundary; variable is changed at process scope
    final BpmnModelInstance process =
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
                        .startEvent("subStart")
                        .serviceTask(INNER_TASK_ID, t -> t.zeebeJobType("inner"))
                        .boundaryEvent("innerBoundary")
                        .cancelActivity(true)
                        .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                        .endEvent("afterInnerBoundary")
                        .moveToActivity(INNER_TASK_ID)
                        .endEvent("subEnd")
                        .subProcessDone())
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long piKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariable("x", 0).create();

    // ensure MI inner tasks are active
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(piKey)
                .withElementId(INNER_TASK_ID)
                .limit(3))
        .hasSize(3);

    // when - variable x is updated at process scope (visible to all children)
    engine.variables().ofScope(piKey).withDocument(Map.of("x", 11)).update();

    final var innerInstances =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(piKey)
            .withElementId(INNER_TASK_ID)
            .limit(3)
            .toList();
    final var childScopes = innerInstances.stream().map(Record::getKey).toArray(Long[]::new);

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withCatchEventId("innerBoundary")
                .limit(3))
        .hasSize(3)
        .extracting(Record::getValue)
        .extracting(ConditionalSubscriptionRecordValue::getScopeKey)
        .containsExactlyInAnyOrder(childScopes);
  }

  @Test
  public void shouldInterruptOnlySingleMiChildWhenInnerBoundaryTriggers() {
    // given
    final String processId = helper.getBpmnProcessId();

    // MI subprocess; inner service task has interrupting boundary
    final BpmnModelInstance process =
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
                        .startEvent("subStart")
                        .serviceTask(INNER_TASK_ID, t -> t.zeebeJobType("inner"))
                        .boundaryEvent("innerBoundary")
                        .cancelActivity(true)
                        .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                        .endEvent("afterInnerBoundary")
                        .moveToActivity(INNER_TASK_ID)
                        .endEvent("subEnd")
                        .subProcessDone())
            .endEvent("end")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // collect inner instances
    final var innerInstances =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(piKey)
            .withElementId(INNER_TASK_ID)
            .limit(3)
            .toList();

    final long childToInterrupt = innerInstances.getFirst().getKey();

    // when - variable change in one child's local scope triggers boundary there
    engine
        .variables()
        .ofScope(childToInterrupt)
        .withDocument(Map.of("x", 11))
        .withLocalSemantic()
        .update();

    // then - boundary triggers once
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withScopeKey(childToInterrupt)
                .withCatchEventId("innerBoundary")
                .limit(1))
        .hasSize(1);

    // and we can still complete jobs for other child instances (they are unaffected)
    // (complete jobs of other instances; if any were cancelled, completion would fail in real
    // engine)
    final var jobs =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(piKey)
            .withElementId(INNER_TASK_ID)
            .limit(3)
            .filter(
                job ->
                    job.getValue().getElementInstanceKey()
                        != childToInterrupt); // exclude interrupted instance

    jobs.forEach(job -> engine.job().withKey(job.getKey()).complete());

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piKey)
                .withElementId(processId)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerBoundaryOnActivityAfterMiWhenAggregatedVariablesMakeConditionTrue() {
    // given
    final String processId = helper.getBpmnProcessId();

    // MI subprocess followed by a user task with boundary; completing MI children updates "sum"
    final BpmnModelInstance process =
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
                        .startEvent("subStart")
                        .serviceTask("inner", t -> t.zeebeJobType("inner"))
                        .endEvent("subEnd")
                        .subProcessDone())
            .userTask(TASK_ID)
            .boundaryEvent("afterMiBoundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=sum > 10").zeebeVariableNames("sum"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("finalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long piKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariable("sum", 0).create();

    // three inner jobs for MI
    final var innerJobs =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(piKey)
            .withElementId("inner")
            .limit(3)
            .toList();

    // when - each child completion "contributes" to sum; last one pushes it over threshold
    engine.job().withKey(innerJobs.get(0).getKey()).withVariables(Map.of("sum", 5)).complete();
    engine.job().withKey(innerJobs.get(1).getKey()).withVariables(Map.of("sum", 9)).complete();
    engine.job().withKey(innerJobs.get(2).getKey()).withVariables(Map.of("sum", 15)).complete();

    // user task after MI is now instantiated with sum > 10
    awaitElementInstance(piKey, TASK_ID);

    // then - boundary on following activity should trigger
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withCatchEventId("afterMiBoundary")
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerBoundaryWhenVariableSetInInputMappingAfterStartPhase() {
    // given
    final String processId = helper.getBpmnProcessId();

    // Input mapping of the activity sets x so that condition is true
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(
                TASK_ID, t -> t.zeebeInputExpression("=11", "x")) // start phase: create local x
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // wait for the user task to be activated (start phase completed and input mapping applied)
    awaitElementInstance(piKey, TASK_ID);

    // then - boundary evaluated after start phase completes and should trigger deterministically
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(piKey)
                .withCatchEventId("boundary")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);
  }

  // Activity is NOT considered active for boundary purposes during end phase.
  // So variable changes in output mapping MUST NOT trigger the boundary.
  @Test
  public void shouldNotTriggerBoundaryWhenVariableSetInOutputMappingAtEndPhase() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String jobType = "end-phase-job";

    // Service task with output mapping that writes x after execution (end phase)
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(
                TASK_ID,
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeOutputExpression("=11", "x")) // end phase: output mapping
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long piKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariable("x", 0).create();

    final var job =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(piKey)
            .withElementId(TASK_ID)
            .getFirst();

    // when - completing job; x is written via output mapping in end phase
    engine.job().withKey(job.getKey()).complete();

    // then - boundary must NOT trigger due to end-phase variable change
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.TRIGGER)
                .onlyCommandRejections()
                .withProcessInstanceKey(piKey)
                .withCatchEventId("boundary")
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piKey)
                .withElementId(TASK_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void
      shouldTriggerBoundaryOnLocalVariableUpdateWhenConditionMatchesAfterPartialVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(TASK_ID, t -> t.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .condition(c -> c.condition("=x + y > 10").zeebeVariableNames("x,y"))
            .endEvent("afterBoundary")
            .moveToActivity(TASK_ID)
            .endEvent("normalEnd")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 6)).create();

    final long taskInstanceKey = awaitElementInstance(processInstanceKey, TASK_ID);

    // when (variables created in task scope, condition becomes true)
    engine
        .variables()
        .ofScope(taskInstanceKey)
        .withDocument(Map.of("y", 5))
        .withLocalSemantic()
        .update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary")
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

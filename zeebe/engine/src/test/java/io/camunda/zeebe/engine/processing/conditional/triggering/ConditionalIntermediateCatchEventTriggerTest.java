/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional.triggering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
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
import org.junit.Rule;
import org.junit.Test;

public final class ConditionalIntermediateCatchEventTriggerTest {

  private static final String CATCH_ID = "catchEvent";
  private static final String SIBLING_TASK_ID = "siblingTask";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldTriggerIntermediateCatchEventWhenVariableCreationInCatchEventScope() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long elementInstanceKey = awaitElementInstance(processInstanceKey, CATCH_ID);

    // when
    engine.variables().ofScope(elementInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(CATCH_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldNotTriggerIntermediateCatchEventWhenConditionStaysFalseOnUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long elementInstanceKey = awaitElementInstance(processInstanceKey, CATCH_ID);

    // when (x is created/updated but condition remains false)
    engine.variables().ofScope(elementInstanceKey).withDocument(Map.of("x", 5)).update();

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
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldTriggerIntermediateCatchEventWhenParentProcessVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when (variable updated in process scope, visible to intermediate catch event)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.records()
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(CATCH_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerIntermediateCatchEventWhenVariableSetInInputMappingAfterStartPhase() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                .zeebeInputExpression("= 20", "x")
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // wait for the intermediate catch event to be activated (start phase completed and input
    // mapping applied)
    awaitElementInstance(processInstanceKey, CATCH_ID);

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(CATCH_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldNotTriggerIntermediateCatchEventWhenVariableSetInOutputMappingAtEndPhase() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(c -> c.condition("=x > 10 or y > 10").zeebeVariableNames("x,y"))
                .zeebeOutputExpression("= 30", "x")
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when - x is written via output mapping in end phase
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("y", 20)).update();

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
                .withCondition("=x > 10 or y > 10")
                .withVariableNames(List.of("x", "y"))
                .withVariableEvents(List.of()))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerIntermediateCatchEventWithoutVariableNameFilter() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(c -> c.condition("=x > 10"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long elementInstanceKey = awaitElementInstance(processInstanceKey, CATCH_ID);

    // when
    engine.variables().ofScope(elementInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.records()
                .conditionalSubscriptionRecords()
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
                .withElementId(CATCH_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldNotTriggerIntermediateCatchEventWhenNonFilteredVariableChanges() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long elementInstanceKey = awaitElementInstance(processInstanceKey, CATCH_ID);

    // when
    engine.variables().ofScope(elementInstanceKey).withDocument(Map.of("y", 20)).update();

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
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldTriggerIntermediateCatchEventOnCreateEventWhenVariableEventsCreate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(
                    c ->
                        c.condition("=x > 10")
                            .zeebeVariableNames("x")
                            .zeebeVariableEvents("create"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long elementInstanceKey = awaitElementInstance(processInstanceKey, CATCH_ID);

    // when - x is created in intermediate catch event  scope, and condition becomes true
    engine.variables().ofScope(elementInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of("create"))
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldNotTriggerIntermediateCatchEventOnCreateEventWhenVariableEventsUpdateOnly() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(
                    c ->
                        c.condition("=x > 10")
                            .zeebeVariableNames("x")
                            .zeebeVariableEvents("update"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long elementInstanceKey = awaitElementInstance(processInstanceKey, CATCH_ID);

    // when
    engine.variables().ofScope(elementInstanceKey).withDocument(Map.of("x", 20)).update();

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
                .withVariableNames("x")
                .withVariableEvents(List.of("update"))
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerIntermediateCatchEventOnUpdateEventWhenVariableEventsCreateOnly() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(
                    c ->
                        c.condition("=x > 10")
                            .zeebeVariableNames("x")
                            .zeebeVariableEvents("create"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariable("x", 5).create();
    final long elementInstanceKey = awaitElementInstance(processInstanceKey, CATCH_ID);

    // when
    engine.variables().ofScope(elementInstanceKey).withDocument(Map.of("x", 20)).update();

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
                .withVariableNames("x")
                .withVariableEvents(List.of("create"))
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldTriggerIntermediateCatchEventOnlyOnceWhenVariableEventsCreateAndUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(
                    c ->
                        c.condition("=x > 10")
                            .zeebeVariableNames("x")
                            .zeebeVariableEvents("create,update"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long elementInstanceKey = awaitElementInstance(processInstanceKey, CATCH_ID);

    // when
    engine.variables().ofScope(elementInstanceKey).withDocument(Map.of("x", 20)).update();

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
                .withVariableNames("x")
                .withVariableEvents(List.of("create", "update")))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(CATCH_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerIntermediateCatchEventForSubprocessWhenVariableChanged() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "sub",
                    b ->
                        b.embeddedSubProcess()
                            .startEvent()
                            .intermediateCatchEvent(CATCH_ID)
                            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long elementInstanceKey = awaitElementInstance(processInstanceKey, "sub");

    // when (variable updated in sub-process scope, visible to intermediate catch event)
    engine.variables().ofScope(elementInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(CATCH_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerMatchingIntermediateCatchEventForSubprocessWhenVariableChanged() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway("fork")
            .subProcess(
                "sub1",
                b ->
                    b.embeddedSubProcess()
                        .startEvent()
                        .intermediateCatchEvent("catch_1")
                        .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                        .endEvent())
            .parallelGateway("join")
            .endEvent()
            .moveToNode("fork")
            .subProcess(
                "sub2",
                b ->
                    b.embeddedSubProcess()
                        .startEvent()
                        .intermediateCatchEvent("catch_2")
                        .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                        .endEvent())
            .connectTo("join")
            .done();

    engine.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long sub1InstanceKey = awaitElementInstance(processInstanceKey, "sub1");

    // when - variable changed only in subprocess instance 1 scope
    engine
        .variables()
        .ofScope(sub1InstanceKey)
        .withDocument(Map.of("x", 20))
        .withLocalSemantic()
        .update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    // catch_1 triggers
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("catch_1")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    // and catch_2 does NOT trigger
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withCatchEventId("catch_2")
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldTriggerIntermediateCatchEventForSubProcessWhenParentProcessVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "sub",
                    b ->
                        b.embeddedSubProcess()
                            .startEvent()
                            .intermediateCatchEvent(CATCH_ID)
                            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when - (variable updated in process scope, visible to intermediate catch event)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(CATCH_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerOuterAndInnerIntermediateCatchEventsWhenVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway("fork")
                .subProcess(
                    "sub",
                    b ->
                        b.embeddedSubProcess()
                            .startEvent()
                            .intermediateCatchEvent("inner")
                            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                            .endEvent())
                .parallelGateway("join")
                .moveToNode("fork")
                .intermediateCatchEvent("outer")
                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                .connectTo("join")
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when - (variable updated in process scope, visible to intermediate catch event)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .limit(2))
        .extracting(Record::getValue)
        .extracting(ConditionalSubscriptionRecordValue::getCatchEventId)
        .containsExactly("outer", "inner");
    // top-down lookup, prioritizing the activation of 'outer' scopes.
  }

  @Test
  public void shouldTriggerInnerCatchEventsWhenVariableChangedAtProcessScopeInMi() {
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "sub",
                    sub ->
                        sub.multiInstance(
                                m ->
                                    m.zeebeInputCollectionExpression("[1,2,3]")
                                        .zeebeInputElement("item"))
                            .embeddedSubProcess()
                            .startEvent()
                            .intermediateCatchEvent(CATCH_ID)
                            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when - (variable updated in process scope, visible to intermediate catch event)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(3))
        .hasSize(3);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementId(CATCH_ID)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .hasSize(3);
  }

  @Test
  public void shouldNotTriggerOtherInnerCatchEventsWhenLocalVariableUpdateInMi() {
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "sub",
                    sub ->
                        sub.multiInstance(
                                m ->
                                    m.zeebeInputCollectionExpression("[1,2,3]")
                                        .zeebeInputElement("item"))
                            .embeddedSubProcess()
                            .startEvent()
                            .intermediateCatchEvent(CATCH_ID)
                            .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // collect inner instances
    final var innerInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(CATCH_ID)
            .limit(3)
            .map(Record::getKey)
            .toList();

    // when
    engine
        .variables()
        .ofScope(innerInstanceKeys.getFirst())
        .withDocument(Map.of("x", 20))
        .withLocalSemantic()
        .update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then - intermediate catch event triggers once
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(innerInstanceKeys.getFirst())
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of()))
        .hasSize(1);

    // (variable updated in process scope, visible to all intermediate catch events)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 20)).update();

    assertThat(
            RecordingExporter.records()
                .skipUntil(r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(2))
        .hasSize(2);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(processId)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerIntermediateCatchEventWhenConditionMatchesAfterPartialVariableUpdate() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(c -> c.condition("=x + y > 10").zeebeVariableNames("x,y"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 6)).create();

    final long elementInstance = awaitElementInstance(processInstanceKey, CATCH_ID);

    // when
    engine
        .variables()
        .ofScope(elementInstance)
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
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(CATCH_ID)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerIntermediateCatchEventRepeatedlyInLoopStructure() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                .zeebeOutputExpression("= loopCounter + 1", "loopCounter")
                .exclusiveGateway("fork")
                .defaultFlow()
                .connectTo(CATCH_ID)
                .moveToNode("fork")
                .conditionExpression("= loopCounter = 3")
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("loopCounter", 0))
            .create();

    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 15)).update();

    final Long[] elementInstanceKeys =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId(CATCH_ID)
            .limit(3)
            .map(Record::getKey)
            .toArray(Long[]::new);

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(3))
        .extracting(Record::getValue)
        .extracting(ConditionalSubscriptionRecordValue::getScopeKey)
        .containsExactly(elementInstanceKeys);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(processId)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldNotTriggerIntermediateCatchEventOnExpressionEvaluationFailure() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .condition(c -> c.condition("= 10 / x > 2").zeebeVariableNames("x"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long elementInstanceKey = awaitElementInstance(processInstanceKey, CATCH_ID);

    // when - update x to 0
    engine.variables().ofScope(elementInstanceKey).withDocument(Map.of("x", 0)).update();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then:
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("= 10 / x > 2")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerIntermediateCatchEventOnSiblingTaskLocalVariableUpdate() {
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
            .intermediateCatchEvent(CATCH_ID)
            .condition(c -> c.condition("= x > 10").zeebeVariableNames("x"))
            .endEvent()
            .moveToNode(CATCH_ID)
            .connectTo("join")
            .done();

    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final long siblingTaskInstanceKey = awaitElementInstance(processInstanceKey, SIBLING_TASK_ID);

    // when (variable updated in sibling task local scope)
    engine
        .variables()
        .ofScope(siblingTaskInstanceKey)
        .withDocument(Map.of("x", 20))
        .withLocalSemantic()
        .update();

    // then
    engine.signal().withSignalName("testSpeedUp").broadcast();

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("= x > 10")
                .withVariableNames("x")
                .withVariableEvents(List.of())
                .limit(1))
        .isEmpty();
  }

  @Test
  public void shouldTriggerIntermediateCatchEventAfterMiWhenAggregatedVariablesMakeConditionTrue() {
    // given
    final String processId = helper.getBpmnProcessId();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .subProcess(
                "sub",
                sub ->
                    sub.multiInstance(
                            b ->
                                b.zeebeInputCollectionExpression("[1,2,3]")
                                    .zeebeInputElement("item"))
                        .embeddedSubProcess()
                        .startEvent()
                        .serviceTask("inner", t -> t.zeebeJobType("inner"))
                        .endEvent()
                        .subProcessDone())
            .intermediateCatchEvent(CATCH_ID)
            .condition(c -> c.condition("=sum > 10").zeebeVariableNames("sum"))
            .endEvent()
            .done();

    engine.deployment().withXmlResource(process).deploy();

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

    awaitElementInstance(processInstanceKey, CATCH_ID);

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId(CATCH_ID)
                .withCondition("=sum > 10")
                .withVariableNames("sum")
                .withVariableEvents(List.of())
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

public class ConditionalStartEventTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCreateSubscriptionForEachRootLevelConditionalStartEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var builder = Bpmn.createExecutableProcess(processId);

    builder.startEvent("startEvent1").condition(c -> c.condition("=x > 10")).endEvent("end1");
    builder.startEvent("startEvent2").condition(c -> c.condition("=y < 5")).endEvent("end2");

    // when
    final var deployment = engine.deployment().withXmlResource(builder.done()).deploy();
    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ConditionalSubscriptionRecordValue::getCatchEventId,
            ConditionalSubscriptionRecordValue::getCondition)
        .hasSize(2)
        .contains(tuple("startEvent1", "=x > 10"), tuple("startEvent2", "=y < 5"));
  }

  @Test
  public void shouldCreateSubscriptionWhenConditionalStartEventAddedOnRedeploy() {
    // given v1 without conditional start
    final String processId = helper.getBpmnProcessId();
    final var deploymentV1 =
        engine
            .deployment()
            .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            .deploy();

    final long processDefinitionKeyV1 =
        deploymentV1.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // sanity check: v1 has no subscription
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKeyV1)
                .limit(1))
        .isEmpty();

    // when v2 adds conditional start
    final var deploymentV2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent")
                    .condition(c -> c.condition("=x > 5"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKeyV2 =
        deploymentV2.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKeyV2)
                .withCatchEventId("startEvent")
                .withCondition("=x > 5")
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldNotCreateSubscriptionWhenConditionalStartEventRemovedOnRedeploy() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var deploymentV1 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent")
                    .condition(c -> c.condition("=x > 1"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKeyV1 =
        deploymentV1.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // sanity: v1 created a subscription
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKeyV1)
                .withCatchEventId("startEvent")
                .limit(1))
        .hasSize(1);

    // when v2 removes conditional start (plain none start event)
    final var deploymentV2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId).startEvent("startEvent").endEvent().done())
            .deploy();

    final long processDefinitionKeyV2 =
        deploymentV2.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKeyV2)
                .limit(1))
        .isEmpty();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.DELETED)
                .withProcessDefinitionKey(processDefinitionKeyV1)
                .withCatchEventId("startEvent")
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldRecreateSubscriptionOnRedeployWhenConditionChanges() {
    // given v1 with condition "=x > y"
    final String processId = helper.getBpmnProcessId();
    final var deploymentV1 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent")
                    .condition(c -> c.condition("=x > y"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKeyV1 =
        deploymentV1.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKeyV1)
                .withCatchEventId("startEvent")
                .withCondition("=x > y")
                .limit(1))
        .hasSize(1);

    // when v2 changes condition to "=x < y"
    final var deploymentV2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent")
                    .condition(c -> c.condition("=x < y"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKeyV2 =
        deploymentV2.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.DELETED)
                .withProcessDefinitionKey(processDefinitionKeyV1)
                .withCatchEventId("startEvent")
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKeyV2)
                .withCatchEventId("startEvent")
                .withCondition("=x < y")
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldRecreateSubscriptionOnRedeployWithSameCondition() {
    // given v1 with condition "=x > 0"
    final String processId = helper.getBpmnProcessId();
    final var deploymentV1 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent")
                    .condition(c -> c.condition("=x > 0"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKeyV1 =
        deploymentV1.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKeyV1)
                .withCatchEventId("startEvent")
                .withCondition("=x > 0")
                .limit(1))
        .hasSize(1);

    // when v2 redeploys with same condition
    final var deploymentV2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent")
                    .condition(c -> c.condition("=x > 0"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKeyV2 =
        deploymentV2.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.DELETED)
                .withProcessDefinitionKey(processDefinitionKeyV1)
                .withCatchEventId("startEvent")
                .withCondition("=x > 0")
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKeyV2)
                .withCatchEventId("startEvent")
                .withCondition("=x > 0")
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldKeepSubscriptionsOfOtherProcessesOnRedeploy() {
    // given P1 and P2 both with conditional start events
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";
    final var deploymentP1V1 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent("startEvent1")
                    .condition(c -> c.condition("=x > 1"))
                    .endEvent()
                    .done())
            .deploy();

    final var deploymentP2V1 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent("startEvent2")
                    .condition(c -> c.condition("=y > 2"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKeyP1V1 =
        deploymentP1V1.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();
    final long processDefinitionKeyP2V1 =
        deploymentP2V1.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.CREATED)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ConditionalSubscriptionRecordValue::getProcessDefinitionKey,
            ConditionalSubscriptionRecordValue::getBpmnProcessId,
            ConditionalSubscriptionRecordValue::getCatchEventId,
            ConditionalSubscriptionRecordValue::getCondition)
        .hasSize(2)
        .contains(
            tuple(processDefinitionKeyP1V1, processId1, "startEvent1", "=x > 1"),
            tuple(processDefinitionKeyP2V1, processId2, "startEvent2", "=y > 2"));

    // when P1 is redeployed with a different condition
    final var deploymentP1V2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent("startEvent1")
                    .condition(c -> c.condition("=x > 10"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKeyP1V2 =
        deploymentP1V2.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    engine.signal().withSignalName("testSpeedUp").broadcast();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.DELETED)
                .withProcessDefinitionKey(processDefinitionKeyP1V1)
                .withBpmnProcessId(processId1)
                .withCatchEventId("startEvent1")
                .withCondition("=x > 1")
                .limit(1))
        .hasSize(1);
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKeyP1V2)
                .withBpmnProcessId(processId1)
                .withCatchEventId("startEvent1")
                .withCondition("=x > 10")
                .limit(1))
        .hasSize(1);

    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == DeploymentIntent.CREATED,
                    r -> r.getIntent() == SignalIntent.BROADCASTED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.DELETED)
                .withProcessDefinitionKey(processDefinitionKeyP2V1)
                .withBpmnProcessId(processId2)
                .withCatchEventId("startEvent2")
                .withCondition("=y > 2")
                .limit(1))
        .isEmpty();
  }
}

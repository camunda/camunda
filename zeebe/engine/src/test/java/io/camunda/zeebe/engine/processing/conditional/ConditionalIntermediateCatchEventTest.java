/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public final class ConditionalIntermediateCatchEventTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldTriggerOnIntermediateCatchEventActivationWhenConditionIsTrue() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent("catchEvent")
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("x", 2, "y", 1))
            .create();

    // then
    final long catchEventKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("catchEvent")
            .getFirst()
            .getKey();

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.TRIGGER)
                .withRecordKey(subscriptionKey)
                .withScopeKey(catchEventKey)
                .withElementInstanceKey(catchEventKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withCatchEventId("catchEvent")
                .withCondition("=x > y")
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerOnIntermediateCatchEventActivationWhenConditionIsTrueWithoutFilters() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent("catchEvent")
                    .condition(c -> c.condition("=x > y"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("x", 2, "y", 1))
            .create();

    // then
    final long catchEventKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("catchEvent")
            .getFirst()
            .getKey();

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.TRIGGER)
                .withRecordKey(subscriptionKey)
                .withScopeKey(catchEventKey)
                .withElementInstanceKey(catchEventKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withCatchEventId("catchEvent")
                .withCondition("=x > y")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerOnIntermediateCatchEventActivationWhenConditionIsTrueForCustomTenant() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String tenantId = "tenant1";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent("catchEvent")
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .done())
            .withTenantId(tenantId)
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withTenantId(tenantId)
            .withVariables(Map.of("x", 2, "y", 1))
            .create();

    // then
    final long catchEventKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("catchEvent")
            .getFirst()
            .getKey();

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.TRIGGER)
                .withRecordKey(subscriptionKey)
                .withScopeKey(catchEventKey)
                .withElementInstanceKey(catchEventKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withCatchEventId("catchEvent")
                .withCondition("=x > y")
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(true)
                .withTenantId(tenantId)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerMultipleTimesOnIntermediateCatchEventActivationWhenConditionIsTrue() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .parallelGateway("fork")
                    .intermediateCatchEvent("catchEvent1")
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .moveToLastGateway()
                    .intermediateCatchEvent("catchEvent2")
                    .condition(
                        c ->
                            c.condition("=x != y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("x", 2, "y", 1))
            .create();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.TRIGGER)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ConditionalSubscriptionRecordValue::getCatchEventId,
            ConditionalSubscriptionRecordValue::getCondition)
        .containsExactlyInAnyOrder(tuple("catchEvent1", "=x > y"), tuple("catchEvent2", "=x != y"));
  }

  @Test
  public void shouldNotTriggerOnIntermediateCatchEventActivationWhenConditionIsFalse() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("catchEvent")
                .condition(
                    c ->
                        c.condition("=x > y")
                            .zeebeVariableNames("x, y")
                            .zeebeVariableEvents("create, update"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariables(Map.of("x", 1, "y", 2))
        .create();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r ->
                        r.getIntent() == ConditionalSubscriptionIntent.CREATED
                            && r.getValueType() == ValueType.CONDITIONAL_SUBSCRIPTION,
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getElementId()
                                .equals("catchEvent"))
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerOnIntermediateCatchEventActivationWhenConditionIsNull() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("catchEvent")
                .condition(
                    c ->
                        c.condition("=x > y")
                            .zeebeVariableNames("x, y")
                            .zeebeVariableEvents("create, update"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 1)).create();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r ->
                        r.getIntent() == ConditionalSubscriptionIntent.CREATED
                            && r.getValueType() == ValueType.CONDITIONAL_SUBSCRIPTION,
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getElementId()
                                .equals("catchEvent"))
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerOnIntermediateCatchEventActivationWhenConditionIsNonBoolean() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("catchEvent")
                .condition(
                    c ->
                        c.condition("=x")
                            .zeebeVariableNames("x")
                            .zeebeVariableEvents("create, update"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", "abc")).create();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r ->
                        r.getIntent() == ConditionalSubscriptionIntent.CREATED
                            && r.getValueType() == ValueType.CONDITIONAL_SUBSCRIPTION,
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getElementId()
                                .equals("catchEvent"))
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerOnIntermediateCatchEventActivationWhenConditionEvaluationFails() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("catchEvent")
                .condition(c -> c.condition("=assert(doesNotExist, doesNotExist != null)"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r ->
                        r.getIntent() == ConditionalSubscriptionIntent.CREATED
                            && r.getValueType() == ValueType.CONDITIONAL_SUBSCRIPTION,
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getElementId()
                                .equals("catchEvent"))
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }
}

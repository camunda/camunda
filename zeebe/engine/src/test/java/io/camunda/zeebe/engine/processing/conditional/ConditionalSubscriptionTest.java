/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

public final class ConditionalSubscriptionTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCreateConditionalSubscriptionsOnBoundaryEventActivation() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("task")
                    .zeebeJobType("task")
                    .boundaryEvent("boundary")
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .moveToActivity("task")
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId("boundary")
                .withCondition("=x > y")
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldCreateConditionalSubscriptionsOnIntermediateCatchEventActivation() {
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
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    final long catchEventKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("catchEvent")
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withScopeKey(catchEventKey)
                .withElementInstanceKey(catchEventKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
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
  public void shouldCreateConditionalSubscriptionsOnEventSubprocessStartEventActivation() {
    // given
    final String processId = helper.getBpmnProcessId();
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .endEvent()
                    .moveToProcess(processId)
                    .eventSubProcess()
                    .startEvent("catchEvent")
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .subProcessDone()
                    .done())
            .deploy();
    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();
    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withScopeKey(processInstanceKey)
                .withElementInstanceKey(processInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
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
  public void shouldCreateConditionalSubscriptionsOnRootLevelStartEventActivation() {
    // given/when
    final String processId = helper.getBpmnProcessId();
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent")
                    .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withScopeKey(-1L)
                .withElementInstanceKey(-1L)
                .withProcessInstanceKey(-1L)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId("startEvent")
                .withCondition("=x > y")
                .withVariableNames("x", "y")
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(1))
        .hasSize(1);
  }
}

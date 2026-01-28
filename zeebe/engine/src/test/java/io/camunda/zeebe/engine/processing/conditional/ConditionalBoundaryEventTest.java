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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public final class ConditionalBoundaryEventTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldTriggerOnBoundaryEventActivationWhenConditionIsTrue() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId = "boundary";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId)
                    .zeebeJobType(serviceTaskId)
                    .boundaryEvent(catchEventId)
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .moveToActivity(serviceTaskId)
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
    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(serviceTaskId)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withRecordKey(subscriptionKey)
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId(catchEventId)
                .withCondition("=x > y")
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(3))
        .extracting(Record::getIntent)
        .containsExactly(
            ConditionalSubscriptionIntent.CREATED,
            ConditionalSubscriptionIntent.TRIGGER,
            ConditionalSubscriptionIntent.TRIGGERED);
  }

  @Test
  public void shouldTriggerOnBoundaryEventActivationWhenConditionIsTrueWithMultipleVariables() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId = "boundary";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId)
                    .zeebeJobType(serviceTaskId)
                    .boundaryEvent(catchEventId)
                    .condition(c -> c.condition("=x + y > 10"))
                    .endEvent()
                    .moveToActivity(serviceTaskId)
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("x", 6, "y", 5))
            .create();

    // then
    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(serviceTaskId)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withRecordKey(subscriptionKey)
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId(catchEventId)
                .withCondition("=x + y > 10")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(3))
        .extracting(Record::getIntent)
        .containsExactly(
            ConditionalSubscriptionIntent.CREATED,
            ConditionalSubscriptionIntent.TRIGGER,
            ConditionalSubscriptionIntent.TRIGGERED);
  }

  @Test
  public void shouldTriggerOnBoundaryEventActivationWhenConditionIsTrueWithoutFilters() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId = "boundary";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId)
                    .zeebeJobType(serviceTaskId)
                    .boundaryEvent(catchEventId)
                    .condition(c -> c.condition("=x > y"))
                    .endEvent()
                    .moveToActivity(serviceTaskId)
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
    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(serviceTaskId)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withRecordKey(subscriptionKey)
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId(catchEventId)
                .withCondition("=x > y")
                .withVariableNames(List.of())
                .withVariableEvents(List.of())
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(3))
        .extracting(Record::getIntent)
        .containsExactly(
            ConditionalSubscriptionIntent.CREATED,
            ConditionalSubscriptionIntent.TRIGGER,
            ConditionalSubscriptionIntent.TRIGGERED);
  }

  @Test
  public void shouldTriggerOnBoundaryEventActivationWhenConditionIsTrueForNonInterrupting() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId = "boundary";
    final String catchEventEndId = "end";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId)
                    .zeebeJobType(serviceTaskId)
                    .boundaryEvent(catchEventId)
                    .cancelActivity(false)
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent(catchEventEndId)
                    .moveToActivity(serviceTaskId)
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

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(serviceTaskId)
        .await();

    final Record<JobBatchRecordValue> batchRecord =
        engine.jobs().withType(serviceTaskId).activate();
    engine.job().withKey(batchRecord.getValue().getJobKeys().get(0)).complete();

    // then
    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(serviceTaskId)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(catchEventEndId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventEndId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withRecordKey(subscriptionKey)
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId(catchEventId)
                .withCondition("=x > y")
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(false)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(3))
        .extracting(Record::getIntent)
        .containsExactly(
            ConditionalSubscriptionIntent.CREATED,
            ConditionalSubscriptionIntent.TRIGGER,
            ConditionalSubscriptionIntent.TRIGGERED);
  }

  @Test
  public void shouldTriggerOnBoundaryEventActivationWhenConditionIsTrueForCustomTenant() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String tenantId = "tenant1";
    final String serviceTaskId = "task";
    final String catchEventId = "boundary";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId)
                    .zeebeJobType(serviceTaskId)
                    .boundaryEvent(catchEventId)
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .moveToActivity(serviceTaskId)
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
            .withVariables(Map.of("x", 2, "y", 1))
            .withTenantId(tenantId)
            .create();

    // then
    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(serviceTaskId)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withRecordKey(subscriptionKey)
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId(catchEventId)
                .withCondition("=x > y")
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(true)
                .withTenantId(tenantId)
                .limit(3))
        .extracting(Record::getIntent)
        .containsExactly(
            ConditionalSubscriptionIntent.CREATED,
            ConditionalSubscriptionIntent.TRIGGER,
            ConditionalSubscriptionIntent.TRIGGERED);
  }

  @Test
  public void shouldTriggerMultipleTimesOnBoundaryEventActivationWhenConditionIsTrue() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId1 = "boundary1";
    final String catchEventId2 = "boundary2";
    final String catchEventEndId1 = "end1";
    final String catchEventEndId2 = "end2";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId)
                    .zeebeJobType(serviceTaskId)
                    .boundaryEvent(catchEventId1)
                    .cancelActivity(false)
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent(catchEventEndId1)
                    .moveToActivity(serviceTaskId)
                    .boundaryEvent(catchEventId2)
                    .cancelActivity(false)
                    .condition(
                        c ->
                            c.condition("=x != y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent(catchEventEndId2)
                    .moveToActivity(serviceTaskId)
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

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(serviceTaskId)
        .await();

    final Record<JobBatchRecordValue> batchRecord =
        engine.jobs().withType(serviceTaskId).activate();
    engine.job().withKey(batchRecord.getValue().getJobKeys().get(0)).complete();

    // then
    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(serviceTaskId)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(catchEventId2, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId2, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(catchEventId1, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId1, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(catchEventEndId2, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventEndId2, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(catchEventEndId1, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventEndId1, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(false)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(6))
        .extracting(
            Record::getIntent,
            r -> r.getValue().getCatchEventId(),
            r -> r.getValue().getCondition())
        .containsSubsequence(
            tuple(ConditionalSubscriptionIntent.CREATED, catchEventId2, "=x != y"),
            tuple(ConditionalSubscriptionIntent.TRIGGER, catchEventId2, "=x != y"),
            tuple(ConditionalSubscriptionIntent.CREATED, catchEventId1, "=x > y"),
            tuple(ConditionalSubscriptionIntent.TRIGGER, catchEventId1, "=x > y"),
            tuple(ConditionalSubscriptionIntent.TRIGGERED, catchEventId2, "=x != y"),
            tuple(ConditionalSubscriptionIntent.TRIGGERED, catchEventId1, "=x > y"));
  }

  @Test
  public void shouldRejectConditionalEventTriggerCommandWhenScopeIsAlreadyInterrupted() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId1 = "boundary1";
    final String catchEventId2 = "boundary2";
    final String catchEventEndId1 = "end1";
    final String catchEventEndId2 = "end2";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId)
                    .zeebeJobType(serviceTaskId)
                    .boundaryEvent(catchEventId1)
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent(catchEventEndId1)
                    .moveToActivity(serviceTaskId)
                    .boundaryEvent(catchEventId2)
                    .condition(
                        c ->
                            c.condition("=x != y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent(catchEventEndId2)
                    .moveToActivity(serviceTaskId)
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
    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(serviceTaskId)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(catchEventId2, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId2, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(catchEventEndId2, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventEndId2, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final long subscriptionKey2 =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .withCatchEventId(catchEventId2)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withRecordKey(subscriptionKey2)
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId(catchEventId2)
                .withCondition("=x != y")
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(3))
        .extracting(Record::getIntent)
        .containsExactly(
            ConditionalSubscriptionIntent.CREATED,
            ConditionalSubscriptionIntent.TRIGGER,
            ConditionalSubscriptionIntent.TRIGGERED);

    final long subscriptionKey1 =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .withCatchEventId(catchEventId1)
            .getFirst()
            .getKey();

    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.TRIGGER)
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId(catchEventId1)
                .withCondition("=x > y")
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .onlyCommandRejections()
                .limit(1)
                .getFirst())
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                "Expected to trigger condition subscription with key '%d', but the element with key "
                    + "'%d' is not active anymore for process instance with key '%d' and catch "
                    + "event id '%s'.",
                subscriptionKey1, serviceTaskKey, processInstanceKey, catchEventId1));
  }

  @Test
  public void shouldRejectConditionalEventTriggerCommandWhenSubscriptionDeleted() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId = "boundary";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(serviceTaskId)
                .zeebeJobType(serviceTaskId)
                .boundaryEvent(catchEventId)
                .condition(
                    c ->
                        c.condition("=x > y")
                            .zeebeVariableNames("x, y")
                            .zeebeVariableEvents("create, update"))
                .endEvent()
                .moveToActivity(serviceTaskId)
                .endEvent()
                .done())
        .deploy();

    // when
    // do not set variables yet to avoid triggering the condition
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(serviceTaskId)
        .await();

    engine.pauseProcessing(1);
    engine.stop();

    final var conditionalRecord =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst();
    final long subscriptionKey = conditionalRecord.getKey();
    engine.writeRecords(
        RecordToWrite.event()
            .conditional(ConditionalSubscriptionIntent.DELETED, conditionalRecord.getValue())
            .key(subscriptionKey),
        RecordToWrite.command()
            .conditional(ConditionalSubscriptionIntent.TRIGGER, conditionalRecord.getValue())
            .key(subscriptionKey));

    engine.start();

    // then
    final var rejection =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.TRIGGER)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to trigger condition subscription with key '%d', but no such subscription was "
                    + "found for process instance with key '%d' and catch event id '%s'.",
                subscriptionKey, processInstanceKey, catchEventId));
  }

  @Test
  public void shouldRejectConditionalEventTriggerCommandWhenSubscriptionTriggered() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId = "boundary";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(serviceTaskId)
                .zeebeJobType(serviceTaskId)
                .boundaryEvent(catchEventId)
                .condition(
                    c ->
                        c.condition("=x > y")
                            .zeebeVariableNames("x, y")
                            .zeebeVariableEvents("create, update"))
                .endEvent()
                .moveToActivity(serviceTaskId)
                .endEvent()
                .done())
        .deploy();

    // when
    // do not set variables yet to avoid triggering the condition
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(serviceTaskId)
        .await();

    engine.pauseProcessing(1);
    engine.stop();

    final var conditionalRecord =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst();
    final long subscriptionKey = conditionalRecord.getKey();
    engine.writeRecords(
        RecordToWrite.event()
            .conditional(ConditionalSubscriptionIntent.TRIGGERED, conditionalRecord.getValue())
            .key(subscriptionKey),
        RecordToWrite.command()
            .conditional(ConditionalSubscriptionIntent.TRIGGER, conditionalRecord.getValue())
            .key(subscriptionKey));

    engine.start();

    // then
    final var rejection =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.TRIGGER)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to trigger condition subscription with key '%d', but no such subscription was "
                    + "found for process instance with key '%d' and catch event id '%s'.",
                subscriptionKey, processInstanceKey, catchEventId));
  }

  @Test
  public void shouldNotTriggerOnBoundaryEventActivationWhenConditionIsFalse() {
    // given
    final String processId = helper.getBpmnProcessId();
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
                    r -> r.getIntent() == ConditionalSubscriptionIntent.CREATED,
                    r -> r.getIntent() == JobIntent.CREATED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerOnBoundaryEventActivationWhenConditionIsNull() {
    // given
    final String processId = helper.getBpmnProcessId();
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

    // when
    engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 1)).create();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ConditionalSubscriptionIntent.CREATED,
                    r -> r.getIntent() == JobIntent.CREATED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerOnBoundaryEventActivationWhenConditionIsNonBoolean() {
    // given
    final String processId = helper.getBpmnProcessId();
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
                        c.condition("=x")
                            .zeebeVariableNames("x")
                            .zeebeVariableEvents("create, update"))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();

    // when
    engine.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", "abc")).create();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ConditionalSubscriptionIntent.CREATED,
                    r -> r.getIntent() == JobIntent.CREATED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerOnBoundaryEventActivationWhenConditionEvaluationFails() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task")
                .zeebeJobType("task")
                .boundaryEvent("boundary")
                .condition(c -> c.condition("=assert(doesNotExist, doesNotExist != null)"))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();

    // when
    engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.records()
                .between(
                    r -> r.getIntent() == ConditionalSubscriptionIntent.CREATED,
                    r -> r.getIntent() == JobIntent.CREATED)
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldDeleteSubscriptionOnAttachedActivityCompletion() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId = "boundary";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId)
                    .zeebeJobType(serviceTaskId)
                    .boundaryEvent(catchEventId)
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .moveToActivity(serviceTaskId)
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    // no variables set to avoid triggering the condition
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(serviceTaskId)
        .await();

    final Record<JobBatchRecordValue> batchRecord =
        engine.jobs().withType(serviceTaskId).activate();
    engine.job().withKey(batchRecord.getValue().getJobKeys().get(0)).complete();

    // then
    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(serviceTaskId)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .doesNotContainSubsequence(
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withRecordKey(subscriptionKey)
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId(catchEventId)
                .withCondition("=x > y")
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(true)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(2))
        .extracting(Record::getIntent)
        .containsExactly(
            ConditionalSubscriptionIntent.CREATED, ConditionalSubscriptionIntent.DELETED);
  }

  @Test
  public void shouldDeleteNonInterruptingSubscriptionOnAttachedActivityCompletion() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId = "boundary";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId)
                    .zeebeJobType(serviceTaskId)
                    .boundaryEvent(catchEventId)
                    .cancelActivity(false)
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .moveToActivity(serviceTaskId)
                    .endEvent()
                    .done())
            .deploy();

    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    // no variables set to avoid triggering the condition
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("x", 2, "y", 1))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(serviceTaskId)
        .await();

    final Record<JobBatchRecordValue> batchRecord =
        engine.jobs().withType(serviceTaskId).activate();
    engine.job().withKey(batchRecord.getValue().getJobKeys().get(0)).complete();

    // then
    final long serviceTaskKey =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(serviceTaskId)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withRecordKey(subscriptionKey)
                .withScopeKey(serviceTaskKey)
                .withElementInstanceKey(serviceTaskKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
                .withBpmnProcessId(processId)
                .withCatchEventId(catchEventId)
                .withCondition("=x > y")
                .withVariableNames("x", "y")
                .withVariableEvents("create", "update")
                .isInterrupting(false)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(4))
        .extracting(Record::getIntent)
        .containsExactly(
            ConditionalSubscriptionIntent.CREATED,
            ConditionalSubscriptionIntent.TRIGGER,
            ConditionalSubscriptionIntent.TRIGGERED,
            ConditionalSubscriptionIntent.DELETED);
  }
}

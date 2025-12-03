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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public final class ConditionalEventSubprocessStartEventTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldTriggerOnEventSubprocessStartEventActivationWhenConditionIsTrue() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String catchEventId = "catchEvent";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .endEvent()
                    .moveToProcess(processId)
                    .eventSubProcess()
                    .startEvent(catchEventId)
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
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("x", 2, "y", 1))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
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
                .withScopeKey(processInstanceKey)
                .withElementInstanceKey(processInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
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
  public void
      shouldTriggerOnEventSubprocessStartEventActivationWhenConditionIsTrueWithoutFilters() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String catchEventId = "catchEvent";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .endEvent()
                    .moveToProcess(processId)
                    .eventSubProcess()
                    .startEvent(catchEventId)
                    .condition(c -> c.condition("=x > y"))
                    .endEvent()
                    .subProcessDone()
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
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
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
                .withScopeKey(processInstanceKey)
                .withElementInstanceKey(processInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
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
  public void
      shouldTriggerOnEventSubprocessStartEventActivationWhenConditionIsTrueForNonInterrupting() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String catchEventId = "catchEvent";
    final String serviceTaskId = "task";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId, t -> t.zeebeJobType(serviceTaskId))
                    .endEvent()
                    .moveToProcess(processId)
                    .eventSubProcess()
                    .startEvent(catchEventId)
                    .interrupting(false)
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
    final Long jobKey = batchRecord.getValue().getJobKeys().get(0);
    engine.job().withKey(jobKey).complete();

    // then
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
                .withScopeKey(processInstanceKey)
                .withElementInstanceKey(processInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
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
  public void
      shouldTriggerOnEventSubprocessStartEventActivationWhenConditionIsTrueForCustomTenant() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String tenantId = "tenant1";
    final String catchEventId = "catchEvent";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .endEvent()
                    .moveToProcess(processId)
                    .eventSubProcess()
                    .startEvent(catchEventId)
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent()
                    .subProcessDone()
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
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
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
                .withScopeKey(processInstanceKey)
                .withElementInstanceKey(processInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
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
  public void shouldTriggerMultipleTimesOnEventSubprocessStartEventActivationWhenConditionIsTrue() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String serviceTaskId = "task";
    final String catchEventId1 = "catchEvent1";
    final String catchEventId2 = "catchEvent2";
    final String catchEventEndId1 = "end1";
    final String catchEventEndId2 = "end2";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(serviceTaskId, t -> t.zeebeJobType(serviceTaskId))
                    .endEvent()
                    .moveToProcess(processId)
                    .eventSubProcess()
                    .startEvent(catchEventId1)
                    .interrupting(false)
                    .condition(
                        c ->
                            c.condition("=x > y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent(catchEventEndId1)
                    .subProcessDone()
                    .moveToProcess(processId)
                    .eventSubProcess()
                    .startEvent(catchEventId2)
                    .interrupting(false)
                    .condition(
                        c ->
                            c.condition("=x != y")
                                .zeebeVariableNames("x, y")
                                .zeebeVariableEvents("create, update"))
                    .endEvent(catchEventEndId2)
                    .subProcessDone()
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
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(catchEventId1, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId1, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(catchEventId2, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId2, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(catchEventEndId1, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventEndId1, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(catchEventEndId2, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventEndId2, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(serviceTaskId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withScopeKey(processInstanceKey)
                .withElementInstanceKey(processInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
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
            tuple(ConditionalSubscriptionIntent.CREATED, catchEventId1, "=x > y"),
            tuple(ConditionalSubscriptionIntent.TRIGGER, catchEventId1, "=x > y"),
            tuple(ConditionalSubscriptionIntent.CREATED, catchEventId2, "=x != y"),
            tuple(ConditionalSubscriptionIntent.TRIGGER, catchEventId2, "=x != y"),
            tuple(ConditionalSubscriptionIntent.TRIGGERED, catchEventId1, "=x > y"),
            tuple(ConditionalSubscriptionIntent.TRIGGERED, catchEventId2, "=x != y"));
  }

  @Test
  public void shouldRejectConditionalEventTriggerCommandWhenSubscriptionDeleted() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String catchEventId = "catchEvent";
    final String serviceTaskId = "task";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(serviceTaskId, t -> t.zeebeJobType(serviceTaskId))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess()
                .startEvent(catchEventId)
                .condition(
                    c ->
                        c.condition("=x > y")
                            .zeebeVariableNames("x, y")
                            .zeebeVariableEvents("create, update"))
                .endEvent()
                .subProcessDone()
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
    final String catchEventId = "catchEvent";
    final String serviceTaskId = "task";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(serviceTaskId, t -> t.zeebeJobType(serviceTaskId))
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess()
                .startEvent(catchEventId)
                .condition(
                    c ->
                        c.condition("=x > y")
                            .zeebeVariableNames("x, y")
                            .zeebeVariableEvents("create, update"))
                .endEvent()
                .subProcessDone()
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
  public void shouldNotTriggerOnEventSubprocessStartEventActivationWhenConditionIsFalse() {
    // given
    final String processId = helper.getBpmnProcessId();
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
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getElementId()
                                .equals(processId))
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerOnEventSubprocessStartEventActivationWhenConditionIsNull() {
    // given
    final String processId = helper.getBpmnProcessId();
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
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getElementId()
                                .equals(processId))
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerOnEventSubprocessStartEventActivationWhenConditionIsNonBoolean() {
    // given
    final String processId = helper.getBpmnProcessId();
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
                        c.condition("=x")
                            .zeebeVariableNames("x")
                            .zeebeVariableEvents("create, update"))
                .endEvent()
                .subProcessDone()
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
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getElementId()
                                .equals(processId))
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldNotTriggerOnEventSubprocessStartEventActivationWhenConditionEvaluationFails() {
    // given
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .endEvent()
                .moveToProcess(processId)
                .eventSubProcess()
                .startEvent("catchEvent")
                .condition(c -> c.condition("=assert(doesNotExist, doesNotExist != null)"))
                .endEvent()
                .subProcessDone()
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
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getElementId()
                                .equals(processId))
                .conditionalSubscriptionRecords()
                .withIntent(ConditionalSubscriptionIntent.TRIGGER)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldDeleteSubscriptionOnScopeTermination() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String catchEventId = "catchEvent";
    final String timerStartEventId = "timerStartEvent";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent(timerStartEventId)
                    .timerWithDuration(Duration.ofSeconds(1))
                    .endEvent()
                    .moveToProcess(processId)
                    .eventSubProcess()
                    .startEvent(catchEventId)
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
    // do not set variables yet to avoid triggering the condition
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(timerStartEventId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(timerStartEventId, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(timerStartEventId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(timerStartEventId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final long subscriptionKey =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.conditionalSubscriptionRecords()
                .withRecordKey(subscriptionKey)
                .withScopeKey(processInstanceKey)
                .withElementInstanceKey(processInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(processDefinitionKey)
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
}

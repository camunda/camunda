/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.engine.state.instance.TimerInstance.NO_ELEMENT_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;

public class ResourceDeletionTest {

  private static final String DRG_SINGLE_DECISION = "/dmn/decision-table-with-version-tag-v1.dmn";
  private static final String DRG_SINGLE_DECISION_V2 = "/dmn/decision-table_v2.dmn";
  private static final String DRG_MULTIPLE_DECISIONS = "/dmn/drg-force-user.dmn";
  private static final String RESULT_VARIABLE = "result";
  private static final String FORM = "/form/test-form-1-with-version-tag-v1.form";
  private static final String RESOURCE = "/resource/test-rpa-1.rpa";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteDeletedEventsForSingleDecision() {
    // given
    final long drgKey = deployDrg(DRG_SINGLE_DECISION);

    // when
    engine.resourceDeletion().withResourceKey(drgKey).delete();

    // then
    verifyDecisionIdWithVersionIsDeleted(drgKey, "jedi_or_sith", 1);
    verifyDecisionRequirementsIsDeleted(drgKey);
    verifyResourceDeletionRecords(drgKey);
  }

  @Test
  public void shouldWriteDeletedEventsForMultipleDecisions() {
    // given
    final long drgKey = deployDrg(DRG_MULTIPLE_DECISIONS);

    // when
    engine.resourceDeletion().withResourceKey(drgKey).delete();

    // then
    verifyDecisionIdWithVersionIsDeleted(drgKey, "jedi_or_sith", 1);
    verifyDecisionIdWithVersionIsDeleted(drgKey, "force_user", 1);
    verifyDecisionRequirementsIsDeleted(drgKey);
    verifyResourceDeletionRecords(drgKey);
  }

  @Test
  public void shouldCreateIncidentIfOnlyDecisionVersionIsDeleted() {
    // given
    final long drgKey = deployDrg(DRG_SINGLE_DECISION);
    final var processId = deployProcessWithBusinessRuleTask("jedi_or_sith");

    // when
    engine.resourceDeletion().withResourceKey(drgKey).delete();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst())
        .describedAs("Should create incident when the only version of a decision is deleted")
        .extracting(
            r -> r.getValue().getBpmnProcessId(),
            r -> r.getValue().getElementId(),
            r -> r.getValue().getErrorType(),
            r -> r.getValue().getErrorMessage())
        .containsOnly(
            processId,
            "task",
            ErrorType.CALLED_DECISION_ERROR,
            """
            Expected to evaluate decision 'jedi_or_sith', \
            but no decision found for id 'jedi_or_sith'\
            """);
  }

  @Test
  public void shouldEvaluatePreviousDecisionVersionIfLatestVersionIsDeleted() {
    // given
    final long drgKeyV1 = deployDrg(DRG_SINGLE_DECISION);
    final long drgKeyV2 = deployDrg(DRG_SINGLE_DECISION_V2);
    final var processId = deployProcessWithBusinessRuleTask("jedi_or_sith");

    // when
    engine.resourceDeletion().withResourceKey(drgKeyV2).delete();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .describedAs("Process Instance should be completed")
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.BUSINESS_RULE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BUSINESS_RULE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
                .withProcessInstanceKey(processInstanceKey)
                .withDecisionId("jedi_or_sith")
                .getFirst())
        .describedAs("Should evaluate version 1 of the decision")
        .extracting(Record::getValue)
        .extracting(
            DecisionEvaluationRecordValue::getDecisionId,
            DecisionEvaluationRecordValue::getDecisionVersion,
            DecisionEvaluationRecordValue::getDecisionRequirementsKey)
        .containsOnly("jedi_or_sith", 1, drgKeyV1);
  }

  @Test
  public void shouldEvaluateLatestVersionIfPreviousVersionIsDeleted() {
    // given
    final long drgKeyV1 = deployDrg(DRG_SINGLE_DECISION);
    final long drgKeyV2 = deployDrg(DRG_SINGLE_DECISION_V2);
    final var processId = deployProcessWithBusinessRuleTask("jedi_or_sith");

    // when
    engine.resourceDeletion().withResourceKey(drgKeyV1).delete();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .describedAs("Process Instance should be completed")
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.BUSINESS_RULE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BUSINESS_RULE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
                .withProcessInstanceKey(processInstanceKey)
                .withDecisionId("jedi_or_sith")
                .getFirst())
        .describedAs("Should evaluate version 2 of the decision")
        .extracting(Record::getValue)
        .extracting(
            DecisionEvaluationRecordValue::getDecisionId,
            DecisionEvaluationRecordValue::getDecisionVersion,
            DecisionEvaluationRecordValue::getDecisionRequirementsKey)
        .containsOnly("jedi_or_sith", 2, drgKeyV2);
  }

  @Test
  public void shouldHaveCorrectLifecycleWhenDeletingProcessWithoutRunningInstances() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey = deployProcess(processId);

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    assertThat(
            RecordingExporter.records()
                .onlyEvents()
                .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED)))
        .describedAs("Should write events in correct order")
        .extracting(Record::getIntent)
        .containsExactly(
            ProcessIntent.CREATED,
            DeploymentIntent.CREATED,
            ResourceDeletionIntent.DELETING,
            ProcessIntent.DELETING,
            ProcessIntent.DELETED,
            ResourceDeletionIntent.DELETED);
  }

  @Test
  public void shouldWriteEventsForDeletedProcessWithoutRunningInstances() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey = deployProcess(processId);

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(processDefinitionKey);
  }

  @Test
  public void shouldWriteEventsForDeletedProcessWithBannedInstances() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final var partitionId = Protocol.decodePartitionId(processInstanceKey);

    // Note! We don't register the banned instance using an event. You won't see the Error Event in
    // the log!
    // We need to run banning in a new transaction to avoid corruption of the engine's transaction
    // when modifying the state from the test thread concurrently to processing.
    engine.banInstanceInNewTransaction(partitionId, processInstanceKey);

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(processDefinitionKey);
  }

  @Test
  public void shouldCreateInstanceOfVersionOneWhenVersionTwoIsDeleted() {
    // given
    final var processId = helper.getBpmnProcessId();
    deployProcess(processId);
    final var secondProcessDefinitionKey = deployProcess(processId);
    engine.resourceDeletion().withResourceKey(secondProcessDefinitionKey).delete();

    // when
    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    verifyProcessIdWithVersionIsDeleted(processId, 2);
    verifyResourceDeletionRecords(secondProcessDefinitionKey);
    verifyInstanceOfProcessWithIdAndVersionIsCompleted(processId, 1, processInstanceKey);
  }

  @Test
  public void shouldCreateInstanceOfVersionTwoWhenVersionOneIsDeleted() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var firstProcessDefinitionKey = deployProcess(processId);
    deployProcess(processId);
    engine.resourceDeletion().withResourceKey(firstProcessDefinitionKey).delete();

    // when
    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(firstProcessDefinitionKey);
    verifyInstanceOfProcessWithIdAndVersionIsCompleted(processId, 2, processInstanceKey);
  }

  @Test
  public void shouldCancelTimerStartEventOnDeletion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey = deployProcessWithTimerStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    verifyTimerIsCancelled(processDefinitionKey);
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(processDefinitionKey);
  }

  @Test
  public void shouldCancelAllTimerStartEventsOnDeletion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent1")
                    .timerWithDuration(Duration.ofDays(1))
                    .endEvent("endEvent")
                    .moveToProcess(processId)
                    .startEvent("startEvent2")
                    .timerWithDuration(Duration.ofDays(1))
                    .connectTo("endEvent")
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    verifyTimersAreCancelled(processDefinitionKey, 2);
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(processDefinitionKey);
  }

  @Test
  public void shouldNotCancelTimersIfDeletedVersionIsNotLatest() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long firstProcessDefinitionKey = deployProcess(processId);
    deployProcessWithTimerStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(firstProcessDefinitionKey).delete();

    // then
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(firstProcessDefinitionKey);
    verifyNoTimersAreCancelled();
  }

  @Test
  public void shouldReactivateTimerOfPreviousVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var versionOneProcessDefinitionKey = deployProcessWithTimerStartEvent(processId);
    final var versionTwoProcessDefinitionKey = deployProcessWithTimerStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(versionTwoProcessDefinitionKey).delete();

    // then
    verifyTimerIsCancelled(versionTwoProcessDefinitionKey);
    // The timer is created once on the first deployment, and a second time after the deletion
    verifyTimerIsCreated(versionOneProcessDefinitionKey, 2);
    verifyProcessIdWithVersionIsDeleted(processId, 2);
    verifyResourceDeletionRecords(versionTwoProcessDefinitionKey);
  }

  @Test
  public void shouldReactivateAllTimersOfPreviousVersion() {

    // given
    final var processId = helper.getBpmnProcessId();
    final var versionOneProcessDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent1")
                    .timerWithDuration(Duration.ofDays(1))
                    .endEvent("endEvent")
                    .moveToProcess(processId)
                    .startEvent("startEvent2")
                    .timerWithDuration(Duration.ofDays(1))
                    .connectTo("endEvent")
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();
    final var versionTwoProcessDefinitionKey = deployProcessWithTimerStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(versionTwoProcessDefinitionKey).delete();

    // then
    verifyTimerIsCancelled(versionTwoProcessDefinitionKey);
    // The timer is created twice on the first deployment, and a two more times after the deletion
    verifyTimerIsCreated(versionOneProcessDefinitionKey, 4);
    verifyProcessIdWithVersionIsDeleted(processId, 2);
    verifyResourceDeletionRecords(versionTwoProcessDefinitionKey);
  }

  @Test
  public void shouldUnsubscribeMessageStartEventOnDeletion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey = deployProcessWithMessageStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    verifyMessageStartEventSubscriptionIsDeleted(processDefinitionKey);
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(processDefinitionKey);
  }

  @Test
  public void shouldUnsubscribeAllMessageStartEventsOnDeletion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent1")
                    .message("message1")
                    .endEvent("endEvent")
                    .moveToProcess(processId)
                    .startEvent("startEvent2")
                    .message("message2")
                    .connectTo("endEvent")
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    verifyMessageStartEventSubscriptionIsDeleted(processDefinitionKey, 2);
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(processDefinitionKey);
  }

  @Test
  public void shouldNotUnsubscribeMessagesIfDeletedVersionIsNotLatest() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long firstProcessDefinitionKey = deployProcess(processId);
    deployProcessWithMessageStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(firstProcessDefinitionKey).delete();

    // then
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(firstProcessDefinitionKey);
    verifyNoMessageStartEventSubscriptionsAreDeleted();
  }

  @Test
  public void shouldRecreateMessageSubscriptionOfPreviousVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var versionOneProcessDefinitionKey = deployProcessWithMessageStartEvent(processId);
    final var versionTwoProcessDefinitionKey = deployProcessWithMessageStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(versionTwoProcessDefinitionKey).delete();

    // then
    verifyMessageStartEventSubscriptionIsDeleted(versionTwoProcessDefinitionKey);
    // The subscription is created once on the first deployment and a second time after the deletion
    verifyMessageStartEventSubscriptionIsCreated(versionOneProcessDefinitionKey, 2);
    verifyProcessIdWithVersionIsDeleted(processId, 2);
    verifyResourceDeletionRecords(versionTwoProcessDefinitionKey);
  }

  @Test
  public void shouldRecreateAllMessageSubscriptionsOfPreviousVersion() {

    // given
    final var processId = helper.getBpmnProcessId();
    final var versionOneProcessDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent1")
                    .message("message1")
                    .endEvent("endEvent")
                    .moveToProcess(processId)
                    .startEvent("startEvent2")
                    .message("message2")
                    .connectTo("endEvent")
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();
    final var versionTwoProcessDefinitionKey = deployProcessWithMessageStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(versionTwoProcessDefinitionKey).delete();

    // then
    verifyMessageStartEventSubscriptionIsDeleted(versionTwoProcessDefinitionKey);
    // The subscription is created twice on the first deployment, and two more times after the
    // deletion
    verifyMessageStartEventSubscriptionIsCreated(versionOneProcessDefinitionKey, 4);
    verifyProcessIdWithVersionIsDeleted(processId, 2);
    verifyResourceDeletionRecords(versionTwoProcessDefinitionKey);
  }

  @Test
  public void shouldUnsubscribeSignalStartEventOnDeletion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey = deployProcessWithSignalStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    verifySignalStartEventSubscriptionIsDeleted(processDefinitionKey);
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(processDefinitionKey);
  }

  @Test
  public void shouldUnsubscribeAllSignalStartEventsOnDeletion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent1")
                    .signal("signal1")
                    .endEvent("endEvent")
                    .moveToProcess(processId)
                    .startEvent("startEvent2")
                    .signal("signal2")
                    .connectTo("endEvent")
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    verifySignalStartEventSubscriptionIsDeleted(processDefinitionKey, 2);
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(processDefinitionKey);
  }

  @Test
  public void shouldNotUnsubscribeSignalsIfDeletedVersionIsNotLatest() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long firstProcessDefinitionKey = deployProcess(processId);
    deployProcessWithSignalStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(firstProcessDefinitionKey).delete();

    // then
    verifyProcessIdWithVersionIsDeleted(processId, 1);
    verifyResourceDeletionRecords(firstProcessDefinitionKey);
    verifyNoSignalStartEventSubscriptionsAreDeleted();
  }

  @Test
  public void shouldRecreateSignalSubscriptionOfPreviousVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var versionOneProcessDefinitionKey = deployProcessWithSignalStartEvent(processId);
    final var versionTwoProcessDefinitionKey = deployProcessWithSignalStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(versionTwoProcessDefinitionKey).delete();

    // then
    verifySignalStartEventSubscriptionIsDeleted(versionTwoProcessDefinitionKey);
    // The subscription is created once on the first deployment and a second time after the deletion
    verifySignalStartEventSubscriptionIsCreated(versionOneProcessDefinitionKey, 2);
    verifyProcessIdWithVersionIsDeleted(processId, 2);
    verifyResourceDeletionRecords(versionTwoProcessDefinitionKey);
  }

  @Test
  public void shouldRecreateAllSignalSubscriptionsOfPreviousVersion() {

    // given
    final var processId = helper.getBpmnProcessId();
    final var versionOneProcessDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("startEvent1")
                    .signal("signal1")
                    .endEvent("endEvent")
                    .moveToProcess(processId)
                    .startEvent("startEvent2")
                    .signal("signal2")
                    .connectTo("endEvent")
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();
    final var versionTwoProcessDefinitionKey = deployProcessWithSignalStartEvent(processId);

    // when
    engine.resourceDeletion().withResourceKey(versionTwoProcessDefinitionKey).delete();

    // then
    verifySignalStartEventSubscriptionIsDeleted(versionTwoProcessDefinitionKey);
    // The subscription is created twice on the first deployment, and two more times after the
    // deletion
    verifySignalStartEventSubscriptionIsCreated(versionOneProcessDefinitionKey, 4);
    verifyProcessIdWithVersionIsDeleted(processId, 2);
    verifyResourceDeletionRecords(versionTwoProcessDefinitionKey);
  }

  @Test
  public void shouldWriteDeletedEventsForForm() {
    // given
    final long formKey = deployForm(FORM);

    // when
    engine.resourceDeletion().withResourceKey(formKey).delete();

    // then
    verifyFormIsDeleted(formKey);
    verifyResourceDeletionRecords(formKey);
  }

  @Test
  public void shouldWriteDeletedEventsForResource() {
    // given
    final long resourceKey = deployResource(RESOURCE);

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete();

    // then
    verifyResourceIsDeleted(resourceKey);
    verifyResourceDeletionRecords(resourceKey);
  }

  private long deployDrg(final String drgResource) {
    return engine
        .deployment()
        .withXmlResource(readResource(drgResource), drgResource)
        .deploy()
        .getValue()
        .getDecisionRequirementsMetadata()
        .get(0)
        .getDecisionRequirementsKey();
  }

  private String deployProcessWithBusinessRuleTask(final String decisionId) {
    final String processId = helper.getBpmnProcessId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .businessRuleTask(
                    "task",
                    t -> t.zeebeCalledDecisionId(decisionId).zeebeResultVariable(RESULT_VARIABLE))
                .done())
        .deploy();
    return processId;
  }

  private long deployProcess(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .versionTag("v1.0")
                .startEvent()
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();
  }

  private long deployProcessWithTimerStartEvent(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .timerWithDuration(Duration.ofDays(1))
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();
  }

  private long deployProcessWithMessageStartEvent(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .message("message")
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();
  }

  private long deployProcessWithSignalStartEvent(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().signal("signal").endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();
  }

  private byte[] readResource(final String resourceName) {
    final var resourceAsStream = getClass().getResourceAsStream(resourceName);
    assertThat(resourceAsStream).isNotNull();

    try {
      return resourceAsStream.readAllBytes();
    } catch (final IOException e) {
      fail("Failed to read resource '{}'", resourceName, e);
      return new byte[0];
    }
  }

  private void verifyResourceDeletionRecords(final long key) {
    assertThat(
            RecordingExporter.resourceDeletionRecords()
                .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED)))
        .describedAs("Expect resource to be deleted")
        .extracting(Record::getIntent, r -> r.getValue().getResourceKey())
        .containsOnly(
            tuple(ResourceDeletionIntent.DELETE, key),
            tuple(ResourceDeletionIntent.DELETING, key),
            tuple(ResourceDeletionIntent.DELETED, key));
  }

  private void verifyDecisionRequirementsIsDeleted(final long key) {
    final var drgCreatedRecord =
        RecordingExporter.decisionRequirementsRecords()
            .withDecisionRequirementsKey(key)
            .withIntent(DecisionRequirementsIntent.CREATED)
            .getFirst()
            .getValue();

    final var drgDeletedRecord =
        RecordingExporter.decisionRequirementsRecords()
            .withDecisionRequirementsKey(key)
            .withIntent(DecisionRequirementsIntent.DELETED)
            .getFirst()
            .getValue();

    assertThat(drgDeletedRecord)
        .describedAs("Expect deleted DRG to match the created DRG")
        .extracting(
            DecisionRequirementsMetadataValue::getDecisionRequirementsId,
            DecisionRequirementsMetadataValue::getDecisionRequirementsName,
            DecisionRequirementsMetadataValue::getDecisionRequirementsVersion,
            DecisionRequirementsMetadataValue::getDecisionRequirementsKey,
            DecisionRequirementsMetadataValue::getResourceName,
            DecisionRequirementsMetadataValue::getChecksum)
        .containsOnly(
            drgCreatedRecord.getDecisionRequirementsId(),
            drgCreatedRecord.getDecisionRequirementsName(),
            drgCreatedRecord.getDecisionRequirementsVersion(),
            drgCreatedRecord.getDecisionRequirementsKey(),
            drgCreatedRecord.getResourceName(),
            drgCreatedRecord.getChecksum());
  }

  private void verifyDecisionIdWithVersionIsDeleted(
      final long drgKey, final String decisionId, final int version) {
    final var decisionCreatedRecord =
        RecordingExporter.decisionRecords()
            .withDecisionRequirementsKey(drgKey)
            .withDecisionId(decisionId)
            .withVersion(version)
            .withIntent(DecisionIntent.CREATED)
            .getFirst()
            .getValue();

    final var decisionDeletedRecord =
        RecordingExporter.decisionRecords()
            .withDecisionRequirementsKey(drgKey)
            .withDecisionId(decisionId)
            .withVersion(version)
            .withIntent(DecisionIntent.DELETED)
            .getFirst()
            .getValue();

    assertThat(decisionDeletedRecord)
        .describedAs("Expect deleted decision to match the created decision")
        .extracting(
            DecisionRecordValue::getDecisionId,
            DecisionRecordValue::getDecisionName,
            DecisionRecordValue::getVersion,
            DecisionRecordValue::getVersionTag,
            DecisionRecordValue::getDecisionKey,
            DecisionRecordValue::getDecisionRequirementsId,
            DecisionRecordValue::getDecisionRequirementsKey,
            DecisionRecordValue::isDuplicate,
            DecisionRecordValue::getDeploymentKey)
        .containsOnly(
            decisionCreatedRecord.getDecisionId(),
            decisionCreatedRecord.getDecisionName(),
            decisionCreatedRecord.getVersion(),
            decisionCreatedRecord.getVersionTag(),
            decisionCreatedRecord.getDecisionKey(),
            decisionCreatedRecord.getDecisionRequirementsId(),
            decisionCreatedRecord.getDecisionRequirementsKey(),
            decisionCreatedRecord.isDuplicate(),
            decisionCreatedRecord.getDeploymentKey());
  }

  private void verifyProcessIdWithVersionIsDeleted(final String processId, final int version) {
    final var processCreatedRecord =
        RecordingExporter.processRecords()
            .withIntent(ProcessIntent.CREATED)
            .withBpmnProcessId(processId)
            .withVersion(version)
            .getFirst()
            .getValue();

    assertThat(
            RecordingExporter.processRecords()
                .withIntents(ProcessIntent.DELETING, ProcessIntent.DELETED)
                .withBpmnProcessId(processId)
                .withVersion(version)
                .limit(2))
        .describedAs("Expect deleted process to match created process")
        .hasSize(2)
        .map(Record::getValue)
        .extracting(
            ProcessMetadataValue::getBpmnProcessId,
            ProcessMetadataValue::getResourceName,
            ProcessMetadataValue::getVersion,
            ProcessMetadataValue::getVersionTag,
            ProcessMetadataValue::getProcessDefinitionKey,
            ProcessMetadataValue::getDeploymentKey)
        .containsOnly(
            tuple(
                processCreatedRecord.getBpmnProcessId(),
                processCreatedRecord.getResourceName(),
                processCreatedRecord.getVersion(),
                processCreatedRecord.getVersionTag(),
                processCreatedRecord.getProcessDefinitionKey(),
                processCreatedRecord.getDeploymentKey()));
  }

  private void verifyInstanceOfProcessWithIdAndVersionIsCompleted(
      final String processId, final int version, final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withBpmnProcessId(processId)
                .withVersion(version)
                .withElementType(BpmnElementType.PROCESS)
                .onlyEvents()
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsExactly(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private void verifyTimerIsCancelled(final long processDefinitionKey) {
    verifyTimersAreCancelled(processDefinitionKey, 1);
  }

  private void verifyTimersAreCancelled(final long processDefinitionKey, final int times) {
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(times))
        .describedAs("Timer(s) should be cancelled")
        .hasSize(times)
        .extracting(
            t -> t.getValue().getProcessDefinitionKey(),
            t -> t.getValue().getProcessInstanceKey(),
            t -> t.getValue().getElementInstanceKey())
        .containsOnly(tuple(processDefinitionKey, NO_ELEMENT_INSTANCE, NO_ELEMENT_INSTANCE));
  }

  private void verifyNoTimersAreCancelled() {
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == ResourceDeletionIntent.DELETED)
                .timerRecords()
                .withIntent(TimerIntent.CANCELED)
                .exists())
        .isFalse();
  }

  private void verifyTimerIsCreated(final long processDefinitionKey, final int times) {
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(times))
        .describedAs("%d timers should be created".formatted(times))
        .hasSize(times)
        .extracting(
            t -> t.getValue().getProcessDefinitionKey(),
            t -> t.getValue().getProcessInstanceKey(),
            t -> t.getValue().getElementInstanceKey())
        .containsOnly(tuple(processDefinitionKey, NO_ELEMENT_INSTANCE, NO_ELEMENT_INSTANCE));
  }

  private void verifyMessageStartEventSubscriptionIsDeleted(final long processDefinitionKey) {
    verifyMessageStartEventSubscriptionIsDeleted(processDefinitionKey, 1);
  }

  private void verifyMessageStartEventSubscriptionIsDeleted(
      final long processDefinitionKey, final int times) {
    assertThat(
            RecordingExporter.messageStartEventSubscriptionRecords(
                    MessageStartEventSubscriptionIntent.DELETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(times))
        .describedAs("Message start event subscription(s) should be deleted")
        .hasSize(times)
        .extracting(t -> t.getValue().getProcessDefinitionKey())
        .containsOnly(processDefinitionKey);
  }

  private void verifyNoMessageStartEventSubscriptionsAreDeleted() {
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == ResourceDeletionIntent.DELETED)
                .messageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.DELETED)
                .exists())
        .isFalse();
  }

  private void verifyMessageStartEventSubscriptionIsCreated(
      final long processDefinitionKey, final int times) {
    assertThat(
            RecordingExporter.messageStartEventSubscriptionRecords(
                    MessageStartEventSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(times))
        .describedAs("%d message start event subscriptions should be created".formatted(times))
        .hasSize(times)
        .extracting(t -> t.getValue().getProcessDefinitionKey())
        .containsOnly(processDefinitionKey);
  }

  private void verifySignalStartEventSubscriptionIsDeleted(final long processDefinitionKey) {
    verifySignalStartEventSubscriptionIsDeleted(processDefinitionKey, 1);
  }

  private void verifySignalStartEventSubscriptionIsDeleted(
      final long processDefinitionKey, final int times) {
    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(times))
        .describedAs("Signal start event subscription(s) should be deleted")
        .hasSize(times)
        .extracting(
            t -> t.getValue().getProcessDefinitionKey(),
            t -> t.getValue().getCatchEventInstanceKey())
        .containsOnly(tuple(processDefinitionKey, NO_ELEMENT_INSTANCE));
  }

  private void verifyNoSignalStartEventSubscriptionsAreDeleted() {
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == ResourceDeletionIntent.DELETED)
                .signalSubscriptionRecords()
                .withIntent(SignalSubscriptionIntent.DELETED)
                .exists())
        .isFalse();
  }

  private void verifySignalStartEventSubscriptionIsCreated(
      final long processDefinitionKey, final int times) {
    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(times))
        .describedAs("%d signal start event subscriptions should be created".formatted(times))
        .hasSize(times)
        .extracting(
            t -> t.getValue().getProcessDefinitionKey(),
            t -> t.getValue().getCatchEventInstanceKey())
        .containsOnly(tuple(processDefinitionKey, NO_ELEMENT_INSTANCE));
  }

  private long deployForm(final String formResource) {
    return engine
        .deployment()
        .withXmlResource(readResource(formResource), formResource)
        .deploy()
        .getValue()
        .getFormMetadata()
        .get(0)
        .getFormKey();
  }

  private long deployResource(final String resource) {
    return engine
        .deployment()
        .withJsonResource(readResource(resource), resource)
        .deploy()
        .getValue()
        .getResourceMetadata()
        .getFirst()
        .getResourceKey();
  }

  private void verifyFormIsDeleted(final long formKey) {
    final var formCreatedRecord =
        RecordingExporter.formRecords()
            .withFormKey(formKey)
            .withIntent(FormIntent.CREATED)
            .getFirst()
            .getValue();

    final var formDeletedRecord =
        RecordingExporter.formRecords()
            .withFormKey(formKey)
            .withIntent(FormIntent.DELETED)
            .getFirst()
            .getValue();

    assertThat(formDeletedRecord)
        .describedAs("Expect deleted form to match the created form")
        .extracting(
            Form::getFormId,
            Form::getFormKey,
            Form::getVersion,
            Form::getVersionTag,
            Form::getResourceName,
            Form::getTenantId,
            Form::getDeploymentKey)
        .containsOnly(
            formCreatedRecord.getFormId(),
            formCreatedRecord.getFormKey(),
            formCreatedRecord.getVersion(),
            formCreatedRecord.getVersionTag(),
            formCreatedRecord.getResourceName(),
            formCreatedRecord.getTenantId(),
            formCreatedRecord.getDeploymentKey());
  }

  private void verifyResourceIsDeleted(final long resourceKey) {
    final var resourceCreatedRecord =
        RecordingExporter.resourceRecords()
            .withResourceKey(resourceKey)
            .withIntent(ResourceIntent.CREATED)
            .getFirst()
            .getValue();

    final var resourceDeletedRecord =
        RecordingExporter.resourceRecords()
            .withResourceKey(resourceKey)
            .withIntent(ResourceIntent.DELETED)
            .getFirst()
            .getValue();

    assertThat(resourceDeletedRecord)
        .describedAs("Expect deleted resource to match the created resource")
        .extracting(
            Resource::getResourceId,
            Resource::getResourceKey,
            Resource::getTenantId,
            Resource::getResourceName,
            Resource::getResourceProp,
            Resource::getChecksum,
            Resource::getVersion,
            Resource::getVersionTag,
            Resource::getDeploymentKey)
        .containsOnly(
            resourceCreatedRecord.getResourceId(),
            resourceCreatedRecord.getResourceKey(),
            resourceCreatedRecord.getTenantId(),
            resourceCreatedRecord.getResourceName(),
            resourceCreatedRecord.getResourceProp(),
            resourceCreatedRecord.getChecksum(),
            resourceCreatedRecord.getVersion(),
            resourceCreatedRecord.getVersionTag(),
            resourceCreatedRecord.getDeploymentKey());
  }
}

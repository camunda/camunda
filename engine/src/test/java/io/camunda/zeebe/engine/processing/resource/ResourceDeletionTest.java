/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

public class ResourceDeletionTest {

  private static final String DRG_SINGLE_DECISION = "/dmn/decision-table.dmn";
  private static final String DRG_SINGLE_DECISION_V2 = "/dmn/decision-table_v2.dmn";
  private static final String DRG_MULTIPLE_DECISIONS = "/dmn/drg-force-user.dmn";
  private static final String RESULT_VARIABLE = "result";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteDeletedEventsForSingleDecision() {
    // given
    final long drgKey = deployDrg(DRG_SINGLE_DECISION);

    // when
    engine.resourceDeletion().withResourceKey(drgKey).delete();

    // then
    verifyDecisionIsDeleted(drgKey, "jedi_or_sith", 1);
    verifyDecisionRequirementsIsDeleted(drgKey);
    verifyResourceIsDeleted(drgKey);
  }

  @Test
  public void shouldWriteDeletedEventsForMultipleDecisions() {
    // given
    final long drgKey = deployDrg(DRG_MULTIPLE_DECISIONS);

    // when
    engine.resourceDeletion().withResourceKey(drgKey).delete();

    // then
    verifyDecisionIsDeleted(drgKey, "jedi_or_sith", 1);
    verifyDecisionIsDeleted(drgKey, "force_user", 1);
    verifyDecisionRequirementsIsDeleted(drgKey);
    verifyResourceIsDeleted(drgKey);
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
    verifyProcessIsDeleted(processId, 1);
    verifyResourceIsDeleted(processDefinitionKey);
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
    verifyProcessIsDeleted(processId, 2);
    verifyResourceIsDeleted(secondProcessDefinitionKey);
    verifyProcessInstanceIsCompleted(processId, 1, processInstanceKey);
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
    verifyProcessIsDeleted(processId, 1);
    verifyResourceIsDeleted(firstProcessDefinitionKey);
    verifyProcessInstanceIsCompleted(processId, 2, processInstanceKey);
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

  private void verifyResourceIsDeleted(final long key) {
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

  private void verifyDecisionIsDeleted(
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
            DecisionRecordValue::getDecisionKey,
            DecisionRecordValue::getDecisionRequirementsId,
            DecisionRecordValue::getDecisionRequirementsKey,
            DecisionRecordValue::isDuplicate)
        .containsOnly(
            decisionCreatedRecord.getDecisionId(),
            decisionCreatedRecord.getDecisionName(),
            decisionCreatedRecord.getVersion(),
            decisionCreatedRecord.getDecisionKey(),
            decisionCreatedRecord.getDecisionRequirementsId(),
            decisionCreatedRecord.getDecisionRequirementsKey(),
            decisionCreatedRecord.isDuplicate());
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
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();
  }

  private void verifyProcessIsDeleted(final String processId, final int version) {
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
        .map(Record::getValue)
        .extracting(
            ProcessMetadataValue::getBpmnProcessId,
            ProcessMetadataValue::getResourceName,
            ProcessMetadataValue::getVersion,
            ProcessMetadataValue::getProcessDefinitionKey)
        .containsOnly(
            tuple(
                processCreatedRecord.getBpmnProcessId(),
                processCreatedRecord.getResourceName(),
                processCreatedRecord.getVersion(),
                processCreatedRecord.getProcessDefinitionKey()));
  }

  private void verifyProcessInstanceIsCompleted(
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
}

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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
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
        .extracting(DecisionEvaluationRecordValue::getDecisionId,
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
        .extracting(DecisionEvaluationRecordValue::getDecisionId,
            DecisionEvaluationRecordValue::getDecisionVersion,
            DecisionEvaluationRecordValue::getDecisionRequirementsKey)
        .containsOnly("jedi_or_sith", 2, drgKeyV2);
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
            tuple(ResourceDeletionIntent.DELETE, key), tuple(ResourceDeletionIntent.DELETED, key));
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
}

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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

public class ResourceDeletionTest {

  private static final String DRG_SINGLE_DECISION = "/dmn/decision-table.dmn";
  private static final String DRG_MULTIPLE_DECISIONS = "/dmn/drg-force-user.dmn";

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
}

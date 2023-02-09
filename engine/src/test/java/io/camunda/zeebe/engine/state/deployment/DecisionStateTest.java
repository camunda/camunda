/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ZeebeStateExtension.class)
public final class DecisionStateTest {

  private MutableZeebeState zeebeState;
  private MutableDecisionState decisionState;

  @BeforeEach
  public void setup() {
    decisionState = zeebeState.getDecisionState();
  }

  @DisplayName("should return empty if no decision is deployed")
  @Test
  void shouldReturnEmptyIfNoDecisionIsDeployedForDeploymentId() {
    // when
    final var persistedDecision = decisionState.findLatestDecisionById(wrapString("decision-1"));

    // then
    assertThat(persistedDecision).isEmpty();
  }

  @DisplayName("should return empty if no decision is deployed")
  @Test
  void shouldReturnEmptyIfNoDecisionIsDeployedForDeploymentKey() {
    // when
    final var persistedDecision = decisionState.findDecisionByKey(1L);

    // then
    assertThat(persistedDecision).isEmpty();
  }

  @DisplayName("should return empty if no DRG is deployed by ID")
  @Test
  void shouldReturnEmptyIfNoDrgIsDeployed() {
    // when
    final var persistedDrg = decisionState.findLatestDecisionRequirementsById(wrapString("drg-1"));

    // then
    assertThat(persistedDrg).isEmpty();
  }

  @DisplayName("should return empty if no DRG is deployed by key")
  @Test
  void shouldReturnEmptyIfNoDrgIsDeployedByKey() {
    // when
    final var persistedDrg = decisionState.findDecisionRequirementsByKey(1L);

    // then
    assertThat(persistedDrg).isEmpty();
  }

  @DisplayName("should put the decision and return it with all properties")
  @Test
  void shouldPutDecision() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    final var decisionRecord =
        sampleDecisionRecord().setDecisionRequirementsKey(drg.getDecisionRequirementsKey());
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decisionRecord);

    // when
    final var persistedDecision =
        decisionState.findLatestDecisionById(decisionRecord.getDecisionIdBuffer());

    // then
    assertThat(persistedDecision).isNotEmpty();
    assertThat(bufferAsString(persistedDecision.get().getDecisionId()))
        .isEqualTo(decisionRecord.getDecisionId());
    assertThat(bufferAsString(persistedDecision.get().getDecisionName()))
        .isEqualTo(decisionRecord.getDecisionName());
    assertThat(persistedDecision.get().getDecisionKey()).isEqualTo(decisionRecord.getDecisionKey());
    assertThat(persistedDecision.get().getVersion()).isEqualTo(decisionRecord.getVersion());
    assertThat(bufferAsString(persistedDecision.get().getDecisionRequirementsId()))
        .isEqualTo(decisionRecord.getDecisionRequirementsId());
    assertThat(persistedDecision.get().getDecisionRequirementsKey())
        .isEqualTo(decisionRecord.getDecisionRequirementsKey());
  }

  @DisplayName("should find deployed decision by ID")
  @Test
  void shouldFindDeployedDecisionById() {
    // given
    final var decisionRecord1 =
        sampleDecisionRecord().setDecisionId("decision-1").setDecisionKey(1L);
    final var decisionRecord2 =
        sampleDecisionRecord().setDecisionId("decision-2").setDecisionKey(2L);
    final var drg = sampleDecisionRequirementsRecord();
    decisionState.storeDecisionRequirements(drg);

    decisionState.storeDecisionRecord(decisionRecord1);
    decisionState.storeDecisionRecord(decisionRecord2);

    // when
    final var persistedDecision1 =
        decisionState.findLatestDecisionById(decisionRecord1.getDecisionIdBuffer());
    final var persistedDecision2 =
        decisionState.findLatestDecisionById(decisionRecord2.getDecisionIdBuffer());

    // then
    assertThat(persistedDecision1).isNotEmpty();
    assertThat(bufferAsString(persistedDecision1.get().getDecisionId()))
        .isEqualTo(decisionRecord1.getDecisionId());

    assertThat(persistedDecision2).isNotEmpty();
    assertThat(bufferAsString(persistedDecision2.get().getDecisionId()))
        .isEqualTo(decisionRecord2.getDecisionId());
  }

  @DisplayName("should find deployed decision by KEY")
  @Test
  void shouldFindDeployedDecisionByKey() {
    // given
    final var decisionRecord1 =
        sampleDecisionRecord().setDecisionId("decision-1").setDecisionKey(1L);
    final var decisionRecord2 =
        sampleDecisionRecord().setDecisionId("decision-2").setDecisionKey(2L);
    final var drg = sampleDecisionRequirementsRecord();
    decisionState.storeDecisionRequirements(drg);

    decisionState.storeDecisionRecord(decisionRecord1);
    decisionState.storeDecisionRecord(decisionRecord2);

    // when
    final var persistedDecision1 =
        decisionState.findDecisionByKey(decisionRecord1.getDecisionKey());
    final var persistedDecision2 =
        decisionState.findDecisionByKey(decisionRecord2.getDecisionKey());

    // then
    assertThat(persistedDecision1).isNotEmpty();
    assertThat(persistedDecision1.get().getDecisionKey())
        .isEqualTo(decisionRecord1.getDecisionKey());

    assertThat(persistedDecision2).isNotEmpty();
    assertThat(persistedDecision2.get().getDecisionKey())
        .isEqualTo(decisionRecord2.getDecisionKey());
  }

  @DisplayName("should return the latest version of the deployed decision by ID")
  @Test
  void shouldReturnLatestVersionOfDeployedDecisionById() {
    // given
    final var drgV1 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(1L);
    final var drgV2 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(2L);
    final var drgV3 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(3L);
    final var decisionRecordV1 =
        sampleDecisionRecord()
            .setDecisionKey(1L)
            .setDecisionRequirementsKey(drgV1.getDecisionRequirementsKey())
            .setVersion(1);
    final var decisionRecordV2 =
        sampleDecisionRecord()
            .setDecisionKey(2L)
            .setDecisionRequirementsKey(drgV2.getDecisionRequirementsKey())
            .setVersion(2);
    final var decisionRecordV3 =
        sampleDecisionRecord()
            .setDecisionKey(3L)
            .setDecisionRequirementsKey(drgV3.getDecisionRequirementsKey())
            .setVersion(3);

    decisionState.storeDecisionRequirements(drgV1);
    decisionState.storeDecisionRequirements(drgV2);
    decisionState.storeDecisionRequirements(drgV3);
    decisionState.storeDecisionRecord(decisionRecordV1);
    decisionState.storeDecisionRecord(decisionRecordV3);
    decisionState.storeDecisionRecord(decisionRecordV2);

    // when
    final var persistedDecision =
        decisionState.findLatestDecisionById(decisionRecordV1.getDecisionIdBuffer());

    // then
    assertThat(persistedDecision).isNotEmpty();
    assertThat(persistedDecision.get().getVersion()).isEqualTo(decisionRecordV3.getVersion());
  }

  @DisplayName("should put the DRG and return it with all properties")
  @Test
  void shouldPutDecisionRequirements() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    decisionState.storeDecisionRequirements(drg);

    // when
    final var persistedDrg =
        decisionState.findLatestDecisionRequirementsById(drg.getDecisionRequirementsIdBuffer());

    // then
    assertThat(persistedDrg).isNotEmpty();
    assertThat(bufferAsString(persistedDrg.get().getDecisionRequirementsId()))
        .isEqualTo(drg.getDecisionRequirementsId());
    assertThat(bufferAsString(persistedDrg.get().getDecisionRequirementsName()))
        .isEqualTo(drg.getDecisionRequirementsName());
    assertThat(persistedDrg.get().getDecisionRequirementsKey())
        .isEqualTo(drg.getDecisionRequirementsKey());
    assertThat(persistedDrg.get().getDecisionRequirementsVersion())
        .isEqualTo(drg.getDecisionRequirementsVersion());
    assertThat(bufferAsString(persistedDrg.get().getResourceName()))
        .isEqualTo(drg.getResourceName());
    assertThat(bufferAsArray(persistedDrg.get().getResource()))
        .describedAs("Expect resource to be equal")
        .isEqualTo(drg.getResource());
    assertThat(bufferAsArray(persistedDrg.get().getChecksum()))
        .describedAs("Expect checksum to be equal")
        .isEqualTo(drg.getChecksum());
  }

  @DisplayName("should find deployed DRGs by ID")
  @Test
  void shouldFindDeployedDecisionRequirementsById() {
    // given
    final var drg1 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsId("drg-1")
            .setDecisionRequirementsKey(1L);
    final var drg2 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsId("drg-2")
            .setDecisionRequirementsKey(2L);

    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);

    // when
    final var persistedDrg1 =
        decisionState.findLatestDecisionRequirementsById(drg1.getDecisionRequirementsIdBuffer());
    final var persistedDrg2 =
        decisionState.findLatestDecisionRequirementsById(drg2.getDecisionRequirementsIdBuffer());

    // then
    assertThat(persistedDrg1).isNotEmpty();
    assertThat(bufferAsString(persistedDrg1.get().getDecisionRequirementsId()))
        .isEqualTo(drg1.getDecisionRequirementsId());

    assertThat(persistedDrg2).isNotEmpty();
    assertThat(bufferAsString(persistedDrg2.get().getDecisionRequirementsId()))
        .isEqualTo(drg2.getDecisionRequirementsId());
  }

  @DisplayName("should return the latest version of the deployed DRG by ID")
  @Test
  void shouldReturnLatestVersionOfDeployedDecisionRequirementsById() {
    // given
    final var decisionRecordV1 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(1L)
            .setDecisionRequirementsVersion(1);
    final var decisionRecordV2 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(2L)
            .setDecisionRequirementsVersion(2);
    final var decisionRecordV3 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(3L)
            .setDecisionRequirementsVersion(3);

    decisionState.storeDecisionRequirements(decisionRecordV1);
    decisionState.storeDecisionRequirements(decisionRecordV3);
    decisionState.storeDecisionRequirements(decisionRecordV2);

    // when
    final var persistedDrg =
        decisionState.findLatestDecisionRequirementsById(
            decisionRecordV1.getDecisionRequirementsIdBuffer());

    // then
    assertThat(persistedDrg).isNotEmpty();
    assertThat(persistedDrg.get().getDecisionRequirementsVersion())
        .isEqualTo(decisionRecordV3.getDecisionRequirementsVersion());
  }

  @DisplayName("should find deployed DRGs by key")
  @Test
  void shouldFindDeployedDecisionRequirementsByKey() {
    // given
    final var drg1 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(1L);
    final var drg2 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(2L);

    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);

    // when
    final var persistedDrg1 =
        decisionState.findDecisionRequirementsByKey(drg1.getDecisionRequirementsKey());
    final var persistedDrg2 =
        decisionState.findDecisionRequirementsByKey(drg2.getDecisionRequirementsKey());

    // then
    assertThat(persistedDrg1).isNotEmpty();
    assertThat(persistedDrg1.get().getDecisionRequirementsKey())
        .isEqualTo(drg1.getDecisionRequirementsKey());

    assertThat(persistedDrg2).isNotEmpty();
    assertThat(persistedDrg2.get().getDecisionRequirementsKey())
        .isEqualTo(drg2.getDecisionRequirementsKey());
  }

  @DisplayName("should return empty if no decision found for DRG key")
  @Test
  void shouldReturnEmptyIfNoDecisionFoundForDrgKey() {
    // given
    final var unknownDrgKey = 1L;

    // when
    final var decisions = decisionState.findDecisionsByDecisionRequirementsKey(unknownDrgKey);

    // then
    assertThat(decisions).isEmpty();
  }

  @DisplayName("should find decisions by DRG key")
  @Test
  void shouldFindDecisionsByDrgKey() {
    // given
    final var drg1 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(10L);
    final var drg2 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(20L);

    final var decision1 =
        sampleDecisionRecord()
            .setDecisionKey(1L)
            .setDecisionRequirementsKey(drg1.getDecisionRequirementsKey());
    final var decision2 =
        sampleDecisionRecord()
            .setDecisionKey(2L)
            .setDecisionRequirementsKey(drg1.getDecisionRequirementsKey());
    final var decision3 =
        sampleDecisionRecord()
            .setDecisionKey(3L)
            .setDecisionRequirementsKey(drg2.getDecisionRequirementsKey());

    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);

    decisionState.storeDecisionRecord(decision1);
    decisionState.storeDecisionRecord(decision2);
    decisionState.storeDecisionRecord(decision3);

    // when
    final var decisionsOfDrg1 =
        decisionState.findDecisionsByDecisionRequirementsKey(drg1.getDecisionRequirementsKey());

    final var decisionsOfDrg2 =
        decisionState.findDecisionsByDecisionRequirementsKey(drg2.getDecisionRequirementsKey());

    // then
    assertThat(decisionsOfDrg1)
        .hasSize(2)
        .extracting(PersistedDecision::getDecisionKey)
        .contains(decision1.getDecisionKey(), decision2.getDecisionKey());

    assertThat(decisionsOfDrg2)
        .hasSize(1)
        .extracting(PersistedDecision::getDecisionKey)
        .contains(decision3.getDecisionKey());
  }

  @DisplayName("should not find decision after it has been deleted")
  @Test
  void shouldNotFindDecisionAfterItHasBeenDeleted() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    final var decisionRecord =
        sampleDecisionRecord().setDecisionRequirementsKey(drg.getDecisionRequirementsKey());
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decisionRecord);

    // when
    decisionState.deleteDecision(decisionRecord);

    // then
    assertThat(decisionState.findDecisionByKey(decisionRecord.getDecisionKey()).isEmpty());
    assertThat(decisionState.findLatestDecisionById(decisionRecord.getDecisionIdBuffer()))
        .isEmpty();
    assertThat(
            decisionState.findDecisionsByDecisionRequirementsKey(
                decisionRecord.getDecisionRequirementsKey()))
        .isEmpty();
  }

  @DisplayName("should find version 2 as latest decision after version 1 has been deleted")
  @Test
  void shouldFindVersion2AsLatestDecisionAfterVersion1HasBeenDeleted() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    final var decisionRecord1 =
        sampleDecisionRecord()
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setVersion(1)
            .setDecisionKey(1);
    final var decisionRecord2 =
        sampleDecisionRecord()
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setVersion(2)
            .setDecisionKey(2);
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decisionRecord1);
    decisionState.storeDecisionRecord(decisionRecord2);

    // when
    decisionState.deleteDecision(decisionRecord1);

    // then
    assertThat(decisionState.findDecisionByKey(decisionRecord1.getDecisionKey()).isEmpty());
    final var latestDecisionById =
        decisionState.findLatestDecisionById(decisionRecord1.getDecisionIdBuffer());
    assertThat(latestDecisionById).isNotEmpty();
    assertThat(latestDecisionById.get().getDecisionId())
        .isEqualTo(decisionRecord2.getDecisionIdBuffer());
    assertThat(latestDecisionById.get().getVersion()).isEqualTo(decisionRecord2.getVersion());
  }

  @DisplayName("should find version 1 as latest decision after version 2 has been deleted")
  @Test
  void shouldFindVersion1AsLatestDecisionAfterVersion2HasBeenDeleted() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    final var decisionRecord1 =
        sampleDecisionRecord()
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setVersion(1)
            .setDecisionKey(1);
    final var decisionRecord2 =
        sampleDecisionRecord()
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setVersion(2)
            .setDecisionKey(2);
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decisionRecord1);
    decisionState.storeDecisionRecord(decisionRecord2);

    // when
    decisionState.deleteDecision(decisionRecord2);

    // then
    assertThat(decisionState.findDecisionByKey(decisionRecord2.getDecisionKey()).isEmpty());
    final var latestDecisionById =
        decisionState.findLatestDecisionById(decisionRecord2.getDecisionIdBuffer());
    assertThat(latestDecisionById).isNotEmpty();
    assertThat(latestDecisionById.get().getDecisionId())
        .isEqualTo(decisionRecord1.getDecisionIdBuffer());
    assertThat(latestDecisionById.get().getVersion()).isEqualTo(decisionRecord1.getVersion());
  }

  @DisplayName("should find version 1 as latest when version 2 is skipped and version 3 is deleted")
  @Test
  void shouldFindVersion1AsLatestDecisionWhenVersion2IsSkippedAndVersion3IsDeleted() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    final var decisionRecord1 =
        sampleDecisionRecord()
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setVersion(1)
            .setDecisionKey(1)
            .setDecisionId("decision-id");
    final var decisionRecord3 =
        sampleDecisionRecord()
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setVersion(3)
            .setDecisionKey(3)
            .setDecisionId("decision-id");
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decisionRecord1);
    decisionState.storeDecisionRecord(decisionRecord3);

    // when
    decisionState.deleteDecision(decisionRecord3);

    // then
    assertThat(decisionState.findDecisionByKey(decisionRecord3.getDecisionKey()).isEmpty());
    final var latestDecisionById =
        decisionState.findLatestDecisionById(decisionRecord3.getDecisionIdBuffer());
    assertThat(latestDecisionById).isNotEmpty();
    assertThat(latestDecisionById.get().getDecisionId())
        .isEqualTo(decisionRecord1.getDecisionIdBuffer());
    assertThat(latestDecisionById.get().getVersion()).isEqualTo(decisionRecord1.getVersion());
  }

  private DecisionRecord sampleDecisionRecord() {
    return new DecisionRecord()
        .setDecisionId("decision-id")
        .setDecisionName("decision-name")
        .setVersion(1)
        .setDecisionKey(1L)
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsKey(1L);
  }

  private DecisionRequirementsRecord sampleDecisionRequirementsRecord() {
    return new DecisionRequirementsRecord()
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsName("drg-name")
        .setDecisionRequirementsVersion(1)
        .setDecisionRequirementsKey(1L)
        .setNamespace("namespace")
        .setResourceName("resource-name")
        .setChecksum(wrapString("checksum"))
        .setResource(wrapString("dmn-resource"));
  }
}

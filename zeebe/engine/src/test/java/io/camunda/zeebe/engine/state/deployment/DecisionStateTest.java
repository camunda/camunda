/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.camunda.zeebe.engine.state.immutable.DecisionState.DecisionRequirementsIdentifier;
import io.camunda.zeebe.engine.state.immutable.DecisionState.PersistedDecisionRequirementsVisitor;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith(ProcessingStateExtension.class)
public final class DecisionStateTest {

  private static final String TENANT_ID = "tenant";
  private MutableProcessingState processingState;
  private MutableDecisionState decisionState;

  @BeforeEach
  public void setup() {
    decisionState = processingState.getDecisionState();
  }

  @DisplayName("should return empty if no decision with given ID is deployed")
  @Test
  void shouldReturnEmptyIfNoDecisionIsDeployedForDecisionId() {
    // when
    final var persistedDecision =
        decisionState.findLatestDecisionByIdAndTenant(wrapString("decision-1"), TENANT_ID);

    // then
    assertThat(persistedDecision).isEmpty();
  }

  @DisplayName("should return empty if no decision with given key is deployed")
  @Test
  void shouldReturnEmptyIfNoDecisionIsDeployedForDecisionKey() {
    // when
    final var persistedDecision = decisionState.findDecisionByTenantAndKey(TENANT_ID, 1L);

    // then
    assertThat(persistedDecision).isEmpty();
  }

  @DisplayName("should return empty if no decision with given ID and deployment key is deployed")
  @Test
  void shouldReturnEmptyIfNoDecisionIsDeployedForDecisionIdAndDeploymentKey() {
    // when
    final var persistedDecision =
        decisionState.findDecisionByIdAndDeploymentKey(TENANT_ID, wrapString("decision-1"), 1L);

    // then
    assertThat(persistedDecision).isEmpty();
  }

  @DisplayName("should return empty if no decision with given ID and version tag is deployed")
  @Test
  void shouldReturnEmptyIfNoDecisionIsDeployedForDecisionIdAndVersionTag() {
    // when
    final var persistedDecision =
        decisionState.findDecisionByIdAndVersionTag(TENANT_ID, wrapString("decision-1"), "v1.0");

    // then
    assertThat(persistedDecision).isEmpty();
  }

  @DisplayName("should return empty if no DRG is deployed by ID")
  @Test
  void shouldReturnEmptyIfNoDrgIsDeployed() {
    // when
    final var persistedDrg =
        decisionState.findLatestDecisionRequirementsByTenantAndId(TENANT_ID, wrapString("drg-1"));

    // then
    assertThat(persistedDrg).isEmpty();
  }

  @DisplayName("should return empty if no DRG is deployed by key")
  @Test
  void shouldReturnEmptyIfNoDrgIsDeployedByKey() {
    // when
    final var persistedDrg = decisionState.findDecisionRequirementsByTenantAndKey(TENANT_ID, 1L);

    // then
    assertThat(persistedDrg).isEmpty();
  }

  @DisplayName("should put the decision and return it with all properties")
  @Test
  void shouldPutDecision() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    final var decisionRecord =
        sampleDecisionRecord()
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setVersionTag("v1.0");
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decisionRecord);

    // when
    final var persistedDecision =
        decisionState.findLatestDecisionByIdAndTenant(
            decisionRecord.getDecisionIdBuffer(), TENANT_ID);

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
    assertThat(persistedDecision.get().getDeploymentKey())
        .isEqualTo(decisionRecord.getDeploymentKey());
    assertThat(persistedDecision.get().getVersionTag()).isEqualTo(decisionRecord.getVersionTag());
  }

  @DisplayName(
      "should store decision key by ID and version tag and return decision with all properties")
  @Test
  public void shouldStoreDecisionKeyByDecisionIdAndVersionTag() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    final var decision = sampleDecisionRecord().setVersionTag("v1.0");
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decision);

    // when
    decisionState.storeDecisionKeyByDecisionIdAndVersionTag(decision);

    // then
    assertThat(
            decisionState.findDecisionByIdAndVersionTag(
                TENANT_ID, decision.getDecisionIdBuffer(), "v1.0"))
        .hasValueSatisfying(
            persistedDecision -> {
              assertThat(bufferAsString(persistedDecision.getDecisionId()))
                  .isEqualTo(decision.getDecisionId());
              assertThat(bufferAsString(persistedDecision.getDecisionName()))
                  .isEqualTo(decision.getDecisionName());
              assertThat(persistedDecision.getDecisionKey()).isEqualTo(decision.getDecisionKey());
              assertThat(persistedDecision.getVersion()).isEqualTo(decision.getVersion());
              assertThat(bufferAsString(persistedDecision.getDecisionRequirementsId()))
                  .isEqualTo(decision.getDecisionRequirementsId());
              assertThat(persistedDecision.getDecisionRequirementsKey())
                  .isEqualTo(decision.getDecisionRequirementsKey());
              assertThat(persistedDecision.getDeploymentKey())
                  .isEqualTo(decision.getDeploymentKey());
              assertThat(persistedDecision.getVersionTag()).isEqualTo(decision.getVersionTag());
            });
  }

  @DisplayName("should store decision key by ID and version tag and overwrite existing entry")
  @Test
  public void shouldStoreDecisionKeyByDecisionIdAndVersionTagAndOverwriteExistingEntry() {
    // given
    final var drg1 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsVersion(1)
            .setDecisionRequirementsKey(1L);
    final var drg2 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsVersion(2)
            .setDecisionRequirementsKey(2L);
    final var decisionV1 =
        sampleDecisionRecord()
            .setVersion(1)
            .setVersionTag("v1.0")
            .setDecisionKey(1L)
            .setDeploymentKey(1L)
            .setDecisionRequirementsKey(drg1.getDecisionRequirementsKey());
    final var decisionV1New =
        sampleDecisionRecord()
            .setVersion(2)
            .setVersionTag("v1.0")
            .setDecisionKey(2L)
            .setDeploymentKey(2L)
            .setDecisionRequirementsKey(drg2.getDecisionRequirementsKey());
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);
    decisionState.storeDecisionRecord(decisionV1);
    decisionState.storeDecisionRecord(decisionV1New);
    decisionState.storeDecisionKeyByDecisionIdAndVersionTag(decisionV1);

    // when
    decisionState.storeDecisionKeyByDecisionIdAndVersionTag(decisionV1New);

    // then
    assertThat(
            decisionState.findDecisionByIdAndVersionTag(
                TENANT_ID, decisionV1New.getDecisionIdBuffer(), "v1.0"))
        .hasValueSatisfying(
            persistedDecision -> {
              assertThat(bufferAsString(persistedDecision.getDecisionId()))
                  .isEqualTo(decisionV1New.getDecisionId());
              assertThat(bufferAsString(persistedDecision.getDecisionName()))
                  .isEqualTo(decisionV1New.getDecisionName());
              assertThat(persistedDecision.getDecisionKey())
                  .isEqualTo(decisionV1New.getDecisionKey());
              assertThat(persistedDecision.getVersion()).isEqualTo(decisionV1New.getVersion());
              assertThat(bufferAsString(persistedDecision.getDecisionRequirementsId()))
                  .isEqualTo(decisionV1New.getDecisionRequirementsId());
              assertThat(persistedDecision.getDecisionRequirementsKey())
                  .isEqualTo(decisionV1New.getDecisionRequirementsKey());
              assertThat(persistedDecision.getDeploymentKey())
                  .isEqualTo(decisionV1New.getDeploymentKey());
              assertThat(persistedDecision.getVersionTag())
                  .isEqualTo(decisionV1New.getVersionTag());
            });
  }

  @DisplayName("should not store decision key by ID and version tag if version tag is empty")
  @Test
  public void shouldNotStoreDecisionKeyByDecisionIdAndVersionTagIfVersionTagIsEmpty() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    final var decisionRecord =
        sampleDecisionRecord().setDecisionRequirementsKey(drg.getDecisionRequirementsKey());
    assertThat(decisionRecord.getVersionTag()).isEmpty();
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decisionRecord);

    // when
    decisionState.storeDecisionKeyByDecisionIdAndVersionTag(decisionRecord);

    // then
    assertThat(
            decisionState.findDecisionByIdAndVersionTag(
                TENANT_ID, decisionRecord.getDecisionIdBuffer(), decisionRecord.getVersionTag()))
        .isEmpty();
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
        decisionState.findLatestDecisionByIdAndTenant(
            decisionRecord1.getDecisionIdBuffer(), TENANT_ID);
    final var persistedDecision2 =
        decisionState.findLatestDecisionByIdAndTenant(
            decisionRecord2.getDecisionIdBuffer(), TENANT_ID);

    // then
    assertThat(persistedDecision1).isNotEmpty();
    assertThat(bufferAsString(persistedDecision1.get().getDecisionId()))
        .isEqualTo(decisionRecord1.getDecisionId());

    assertThat(persistedDecision2).isNotEmpty();
    assertThat(bufferAsString(persistedDecision2.get().getDecisionId()))
        .isEqualTo(decisionRecord2.getDecisionId());
  }

  @DisplayName("should find deployed decision by key")
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
        decisionState.findDecisionByTenantAndKey(TENANT_ID, decisionRecord1.getDecisionKey());
    final var persistedDecision2 =
        decisionState.findDecisionByTenantAndKey(TENANT_ID, decisionRecord2.getDecisionKey());

    // then
    assertThat(persistedDecision1).isNotEmpty();
    assertThat(persistedDecision1.get().getDecisionKey())
        .isEqualTo(decisionRecord1.getDecisionKey());

    assertThat(persistedDecision2).isNotEmpty();
    assertThat(persistedDecision2.get().getDecisionKey())
        .isEqualTo(decisionRecord2.getDecisionKey());
  }

  @DisplayName("should find deployed decision by ID and deployment key")
  @Test
  void shouldFindDeployedDecisionByIdAndDeploymentKey() {
    // given
    final var drg1 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsVersion(1)
            .setDecisionRequirementsKey(1L);
    final var drg2 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsVersion(2)
            .setDecisionRequirementsKey(2L);
    final var decision1Version1 =
        sampleDecisionRecord()
            .setDecisionId("decision-1")
            .setVersion(1)
            .setDecisionKey(1L)
            .setDeploymentKey(1L)
            .setDecisionRequirementsKey(drg1.getDecisionRequirementsKey());
    final var decision2Version1 =
        sampleDecisionRecord()
            .setDecisionId("decision-2")
            .setVersion(1)
            .setDecisionKey(2L)
            .setDeploymentKey(1L)
            .setDecisionRequirementsKey(drg1.getDecisionRequirementsKey());
    final var decision1Version2 =
        sampleDecisionRecord()
            .setDecisionId("decision-1")
            .setVersion(2)
            .setDecisionKey(3L)
            .setDeploymentKey(2L)
            .setDecisionRequirementsKey(drg2.getDecisionRequirementsKey());
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);
    decisionState.storeDecisionRecord(decision1Version1);
    decisionState.storeDecisionRecord(decision2Version1);
    decisionState.storeDecisionRecord(decision1Version2);
    decisionState.storeDecisionKeyByDecisionIdAndDeploymentKey(decision1Version1);
    decisionState.storeDecisionKeyByDecisionIdAndDeploymentKey(decision2Version1);
    decisionState.storeDecisionKeyByDecisionIdAndDeploymentKey(decision1Version2);

    // when
    final var persistedDecision1 =
        decisionState.findDecisionByIdAndDeploymentKey(TENANT_ID, wrapString("decision-1"), 1L);
    final var persistedDecision2 =
        decisionState.findDecisionByIdAndDeploymentKey(TENANT_ID, wrapString("decision-2"), 1L);
    final var persistedDecision3 =
        decisionState.findDecisionByIdAndDeploymentKey(TENANT_ID, wrapString("decision-1"), 2L);
    final var persistedDecision4 =
        decisionState.findDecisionByIdAndDeploymentKey(TENANT_ID, wrapString("decision-2"), 2L);

    // then
    assertThat(persistedDecision1)
        .hasValueSatisfying(
            decision -> {
              assertThat(decision.getDecisionKey()).isEqualTo(decision1Version1.getDecisionKey());
              assertThat(bufferAsString(decision.getDecisionId()))
                  .isEqualTo(decision1Version1.getDecisionId());
              assertThat(decision.getVersion()).isEqualTo(decision1Version1.getVersion());
              assertThat(decision.getDeploymentKey())
                  .isEqualTo(decision1Version1.getDeploymentKey());
            });
    assertThat(persistedDecision2)
        .hasValueSatisfying(
            decision -> {
              assertThat(decision.getDecisionKey()).isEqualTo(decision2Version1.getDecisionKey());
              assertThat(bufferAsString(decision.getDecisionId()))
                  .isEqualTo(decision2Version1.getDecisionId());
              assertThat(decision.getVersion()).isEqualTo(decision2Version1.getVersion());
              assertThat(decision.getDeploymentKey())
                  .isEqualTo(decision2Version1.getDeploymentKey());
            });
    assertThat(persistedDecision3)
        .hasValueSatisfying(
            decision -> {
              assertThat(decision.getDecisionKey()).isEqualTo(decision1Version2.getDecisionKey());
              assertThat(bufferAsString(decision.getDecisionId()))
                  .isEqualTo(decision1Version2.getDecisionId());
              assertThat(decision.getVersion()).isEqualTo(decision1Version2.getVersion());
              assertThat(decision.getDeploymentKey())
                  .isEqualTo(decision1Version2.getDeploymentKey());
            });
    assertThat(persistedDecision4).isEmpty();
  }

  @DisplayName("should find deployed decision by ID and version tag")
  @Test
  void shouldFindDeployedDecisionByIdAndVersionTag() {
    // given
    final var drg1 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsVersion(1)
            .setDecisionRequirementsKey(1L);
    final var drg2 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsVersion(2)
            .setDecisionRequirementsKey(2L);
    final var decision1Version1 =
        sampleDecisionRecord()
            .setDecisionId("decision-1")
            .setVersion(1)
            .setVersionTag("v1.0")
            .setDecisionKey(1L)
            .setDeploymentKey(1L)
            .setDecisionRequirementsKey(drg1.getDecisionRequirementsKey());
    final var decision2Version1 =
        sampleDecisionRecord()
            .setDecisionId("decision-2")
            .setVersion(1)
            .setVersionTag("v1.0")
            .setDecisionKey(2L)
            .setDeploymentKey(1L)
            .setDecisionRequirementsKey(drg1.getDecisionRequirementsKey());
    final var decision1Version2 =
        sampleDecisionRecord()
            .setDecisionId("decision-1")
            .setVersion(2)
            .setVersionTag("v2.0")
            .setDecisionKey(3L)
            .setDeploymentKey(2L)
            .setDecisionRequirementsKey(drg2.getDecisionRequirementsKey());
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);
    decisionState.storeDecisionRecord(decision1Version1);
    decisionState.storeDecisionRecord(decision2Version1);
    decisionState.storeDecisionRecord(decision1Version2);
    decisionState.storeDecisionKeyByDecisionIdAndVersionTag(decision1Version1);
    decisionState.storeDecisionKeyByDecisionIdAndVersionTag(decision2Version1);
    decisionState.storeDecisionKeyByDecisionIdAndVersionTag(decision1Version2);

    // when
    final var persistedDecision1 =
        decisionState.findDecisionByIdAndVersionTag(TENANT_ID, wrapString("decision-1"), "v1.0");
    final var persistedDecision2 =
        decisionState.findDecisionByIdAndVersionTag(TENANT_ID, wrapString("decision-2"), "v1.0");
    final var persistedDecision3 =
        decisionState.findDecisionByIdAndVersionTag(TENANT_ID, wrapString("decision-1"), "v2.0");
    final var persistedDecision4 =
        decisionState.findDecisionByIdAndVersionTag(TENANT_ID, wrapString("decision-2"), "v2.0");

    // then
    assertThat(persistedDecision1)
        .map(PersistedDecision::getDecisionKey)
        .hasValue(decision1Version1.getDecisionKey());
    assertThat(persistedDecision2)
        .map(PersistedDecision::getDecisionKey)
        .hasValue(decision2Version1.getDecisionKey());
    assertThat(persistedDecision3)
        .map(PersistedDecision::getDecisionKey)
        .hasValue(decision1Version2.getDecisionKey());
    assertThat(persistedDecision4).isEmpty();
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
        decisionState.findLatestDecisionByIdAndTenant(
            decisionRecordV1.getDecisionIdBuffer(), TENANT_ID);

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
        decisionState.findLatestDecisionRequirementsByTenantAndId(
            TENANT_ID, drg.getDecisionRequirementsIdBuffer());

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
        decisionState.findLatestDecisionRequirementsByTenantAndId(
            TENANT_ID, drg1.getDecisionRequirementsIdBuffer());
    final var persistedDrg2 =
        decisionState.findLatestDecisionRequirementsByTenantAndId(
            TENANT_ID, drg2.getDecisionRequirementsIdBuffer());

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
        decisionState.findLatestDecisionRequirementsByTenantAndId(
            TENANT_ID, decisionRecordV1.getDecisionRequirementsIdBuffer());

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
        decisionState.findDecisionRequirementsByTenantAndKey(
            TENANT_ID, drg1.getDecisionRequirementsKey());
    final var persistedDrg2 =
        decisionState.findDecisionRequirementsByTenantAndKey(
            TENANT_ID, drg2.getDecisionRequirementsKey());

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
    final var decisions =
        decisionState.findDecisionsByTenantAndDecisionRequirementsKey(TENANT_ID, unknownDrgKey);

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
        decisionState.findDecisionsByTenantAndDecisionRequirementsKey(
            TENANT_ID, drg1.getDecisionRequirementsKey());

    final var decisionsOfDrg2 =
        decisionState.findDecisionsByTenantAndDecisionRequirementsKey(
            TENANT_ID, drg2.getDecisionRequirementsKey());

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
        sampleDecisionRecord()
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setVersionTag("v1.0");
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decisionRecord);
    decisionState.storeDecisionKeyByDecisionIdAndDeploymentKey(decisionRecord);
    decisionState.storeDecisionKeyByDecisionIdAndVersionTag(decisionRecord);

    // when
    decisionState.deleteDecision(decisionRecord);

    // then
    assertThat(decisionState.findDecisionByTenantAndKey(TENANT_ID, decisionRecord.getDecisionKey()))
        .isEmpty();
    assertThat(
            decisionState.findLatestDecisionByIdAndTenant(
                decisionRecord.getDecisionIdBuffer(), TENANT_ID))
        .isEmpty();
    assertThat(
            decisionState.findDecisionsByTenantAndDecisionRequirementsKey(
                TENANT_ID, decisionRecord.getDecisionRequirementsKey()))
        .isEmpty();
    assertThat(
            decisionState.findDecisionByIdAndDeploymentKey(
                TENANT_ID, decisionRecord.getDecisionIdBuffer(), decisionRecord.getDeploymentKey()))
        .isEmpty();
    assertThat(
            decisionState.findDecisionByIdAndVersionTag(
                TENANT_ID, decisionRecord.getDecisionIdBuffer(), decisionRecord.getVersionTag()))
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
    assertThat(
            decisionState.findDecisionByTenantAndKey(TENANT_ID, decisionRecord1.getDecisionKey()))
        .isEmpty();
    final var latestDecisionById =
        decisionState.findLatestDecisionByIdAndTenant(
            decisionRecord1.getDecisionIdBuffer(), TENANT_ID);
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
    assertThat(
            decisionState.findDecisionByTenantAndKey(TENANT_ID, decisionRecord2.getDecisionKey()))
        .isEmpty();
    final var latestDecisionById =
        decisionState.findLatestDecisionByIdAndTenant(
            decisionRecord2.getDecisionIdBuffer(), TENANT_ID);
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
    assertThat(
            decisionState.findDecisionByTenantAndKey(TENANT_ID, decisionRecord3.getDecisionKey()))
        .isEmpty();
    final var latestDecisionById =
        decisionState.findLatestDecisionByIdAndTenant(
            decisionRecord3.getDecisionIdBuffer(), TENANT_ID);
    assertThat(latestDecisionById).isNotEmpty();
    assertThat(latestDecisionById.get().getDecisionId())
        .isEqualTo(decisionRecord1.getDecisionIdBuffer());
    assertThat(latestDecisionById.get().getVersion()).isEqualTo(decisionRecord1.getVersion());
  }

  @DisplayName("should not find DRG after it has been deleted")
  @Test
  void shouldNotFindDrgAfterItHasBeenDeleted() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    decisionState.storeDecisionRequirements(drg);

    // when
    decisionState.deleteDecisionRequirements(drg);

    // then
    assertThat(
            decisionState.findDecisionRequirementsByTenantAndKey(
                TENANT_ID, drg.getDecisionRequirementsKey()))
        .isEmpty();
    assertThat(
            decisionState.findLatestDecisionByIdAndTenant(
                drg.getDecisionRequirementsIdBuffer(), TENANT_ID))
        .isEmpty();
  }

  @DisplayName("should find version 2 as latest DRG after version 1 has been deleted")
  @Test
  void shouldFindVersion2AsLatestDrgAfterVersion1HasBeenDeleted() {
    // given
    final var drg1 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(1)
            .setDecisionRequirementsVersion(1);
    final var drg2 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(2)
            .setDecisionRequirementsVersion(2);
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);

    // when
    decisionState.deleteDecisionRequirements(drg1);

    // then
    assertThat(
            decisionState.findDecisionsByTenantAndDecisionRequirementsKey(
                TENANT_ID, drg1.getDecisionRequirementsKey()))
        .isEmpty();
    final var latestDrg =
        decisionState.findLatestDecisionRequirementsByTenantAndId(
            TENANT_ID, drg1.getDecisionRequirementsIdBuffer());
    assertThat(latestDrg).isNotEmpty();
    assertThat(latestDrg.get().getDecisionRequirementsId())
        .isEqualTo(drg2.getDecisionRequirementsIdBuffer());
    assertThat(latestDrg.get().getDecisionRequirementsVersion())
        .isEqualTo(drg2.getDecisionRequirementsVersion());
  }

  @DisplayName("should find version 1 as latest DRG after version 2 has been deleted")
  @Test
  void shouldFindVersion1AsLatestDrgAfterVersion2HasBeenDeleted() {
    // given
    final var drg1 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(1)
            .setDecisionRequirementsVersion(1);
    final var drg2 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(2)
            .setDecisionRequirementsVersion(2);
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);

    // when
    decisionState.deleteDecisionRequirements(drg2);

    // then
    assertThat(
            decisionState.findDecisionByTenantAndKey(TENANT_ID, drg2.getDecisionRequirementsKey()))
        .isEmpty();
    final var latestDrg =
        decisionState.findLatestDecisionRequirementsByTenantAndId(
            TENANT_ID, drg2.getDecisionRequirementsIdBuffer());
    assertThat(latestDrg).isNotEmpty();
    assertThat(latestDrg.get().getDecisionRequirementsId())
        .isEqualTo(drg1.getDecisionRequirementsIdBuffer());
    assertThat(latestDrg.get().getDecisionRequirementsVersion())
        .isEqualTo(drg1.getDecisionRequirementsVersion());
  }

  @DisplayName("should find version 1 as latest when version 2 is skipped and version 3 is deleted")
  @Test
  void shouldFindVersion1AsLatestDrgWhenVersion2IsSkippedAndVersion3IsDeleted() {
    // given
    final var drg1 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(1)
            .setDecisionRequirementsVersion(1);
    final var drg3 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(3)
            .setDecisionRequirementsVersion(3);
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg3);

    // when
    decisionState.deleteDecisionRequirements(drg3);

    // then
    assertThat(
            decisionState.findDecisionByTenantAndKey(TENANT_ID, drg3.getDecisionRequirementsKey()))
        .isEmpty();
    final var latestDrg =
        decisionState.findLatestDecisionRequirementsByTenantAndId(
            TENANT_ID, drg3.getDecisionRequirementsIdBuffer());
    assertThat(latestDrg).isNotEmpty();
    assertThat(latestDrg.get().getDecisionRequirementsId())
        .isEqualTo(drg1.getDecisionRequirementsIdBuffer());
    assertThat(latestDrg.get().getDecisionRequirementsVersion())
        .isEqualTo(drg1.getDecisionRequirementsVersion());
  }

  @Test
  void shouldIterateThroughAllDecisionRequirements() {
    // given
    final var drg1 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(1L);
    final var drg2 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(2L);
    final var drg3 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(3L);
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);
    decisionState.storeDecisionRequirements(drg3);

    // when
    final var visitor = Mockito.mock(LongConsumer.class);
    decisionState.forEachDecisionRequirements(
        null,
        (drg) -> {
          visitor.accept(drg.getDecisionRequirementsKey());
          return true;
        });

    // then
    Mockito.verify(visitor).accept(1L);
    Mockito.verify(visitor).accept(2L);
    Mockito.verify(visitor).accept(3L);
  }

  @Test
  void shouldSkipBeforeIteratingThroughDecisionRequirements() {
    // given
    final var drg1 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(1L);
    final var drg2 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(2L);
    final var drg3 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(3L);
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);
    decisionState.storeDecisionRequirements(drg3);

    // when
    final var visitor = Mockito.mock(PersistedDecisionRequirementsVisitor.class);
    Mockito.when(visitor.visit(any())).thenReturn(true);
    decisionState.forEachDecisionRequirements(
        new DecisionRequirementsIdentifier(drg1.getTenantId(), drg1.getDecisionRequirementsKey()),
        visitor);

    // then
    Mockito.verify(visitor, Mockito.times(2)).visit(any());
  }

  @Test
  public void shouldSetDeploymentKey() {
    // given
    final var noDeploymentKey = -1L;
    final var someDeploymentKey = 1L;

    final var drg = sampleDecisionRequirementsRecord();
    final var decision = sampleDecisionRecord().setDeploymentKey(noDeploymentKey);
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decision);

    // when
    decisionState.setMissingDeploymentKey(
        decision.getTenantId(), decision.getDecisionKey(), someDeploymentKey);

    // then
    final var updatedDecision =
        decisionState
            .findDecisionByTenantAndKey(decision.getTenantId(), decision.getDecisionKey())
            .orElseThrow();
    assertThat(updatedDecision.getDeploymentKey()).isEqualTo(someDeploymentKey);
  }

  @Test
  public void shouldSetDeploymentKeyPersistently() {
    // given
    final var noDeploymentKey = -1L;
    final var someDeploymentKey = 1L;

    final var drg = sampleDecisionRequirementsRecord();
    final var decision = sampleDecisionRecord().setDeploymentKey(noDeploymentKey);
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decision);
    decisionState.setMissingDeploymentKey(
        decision.getTenantId(), decision.getDecisionKey(), someDeploymentKey);

    // when
    decisionState.clearCache();

    // then
    final var updatedDecision =
        decisionState
            .findDecisionByTenantAndKey(decision.getTenantId(), decision.getDecisionKey())
            .orElseThrow();
    assertThat(updatedDecision.getDeploymentKey()).isEqualTo(someDeploymentKey);
  }

  @Test
  public void shouldFindDecisionByDeploymentKeyAfterUpdating() {
    // given
    final var noDeploymentKey = -1L;
    final var someDeploymentKey = 1L;

    final var drg = sampleDecisionRequirementsRecord();
    final var decision = sampleDecisionRecord().setDeploymentKey(noDeploymentKey);
    decisionState.storeDecisionRequirements(drg);
    decisionState.storeDecisionRecord(decision);

    // when
    decisionState.setMissingDeploymentKey(
        decision.getTenantId(), decision.getDecisionKey(), someDeploymentKey);

    // then
    final var foundDecision =
        decisionState
            .findDecisionByIdAndDeploymentKey(
                decision.getTenantId(),
                BufferUtil.wrapString(decision.getDecisionId()),
                someDeploymentKey)
            .orElseThrow();
    assertThat(foundDecision.getDecisionKey()).isEqualTo(decision.getDecisionKey());
  }

  private DecisionRecord sampleDecisionRecord() {
    return new DecisionRecord()
        .setDecisionId("decision-id")
        .setDecisionName("decision-name")
        .setVersion(1)
        .setDecisionKey(1L)
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsKey(1L)
        .setTenantId(TENANT_ID)
        .setDeploymentKey(1L);
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
        .setResource(wrapString("dmn-resource"))
        .setTenantId(TENANT_ID);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class DecisionStateMultiTenantTest {
  private MutableProcessingState processingState;
  private MutableDecisionState decisionState;

  @BeforeEach
  public void setup() {
    decisionState = processingState.getDecisionState();
  }

  @Test
  void shouldPutDrgForDifferentTenants() {
    // given
    final var drgKey = 123L;
    final var drgId = "drgId";
    final int version = 1;
    final var tenant1 = "tenant1";
    final var tenant2 = "tenant2";
    final var drg1 = sampleDecisionRequirementsRecord(tenant1, drgKey, drgId, version);
    final var drg2 = sampleDecisionRequirementsRecord(tenant2, drgKey, drgId, version);

    // when
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);

    // then
    var actualDrg1 = decisionState.findDecisionRequirementsByTenantAndKey(tenant1, drgKey);
    var actualDrg2 = decisionState.findDecisionRequirementsByTenantAndKey(tenant2, drgKey);
    assertDeployedDrg(actualDrg1.get(), tenant1, drgKey, drgId, version);
    assertDeployedDrg(actualDrg2.get(), tenant2, drgKey, drgId, version);

    actualDrg1 =
        decisionState.findLatestDecisionRequirementsByTenantAndId(tenant1, wrapString(drgId));
    actualDrg2 =
        decisionState.findLatestDecisionRequirementsByTenantAndId(tenant2, wrapString(drgId));
    assertDeployedDrg(actualDrg1.get(), tenant1, drgKey, drgId, version);
    assertDeployedDrg(actualDrg2.get(), tenant2, drgKey, drgId, version);
  }

  @Test
  void shouldPutDecisionForDifferentTenants() {
    // given
    final var drgKey = 123L;
    final var drgId = "drgId";
    final var decisionKey = 456L;
    final var decisionId = "decisionId";
    final int version = 1;
    final var tenant1 = "tenant1";
    final var tenant2 = "tenant2";
    final var drg1 = sampleDecisionRequirementsRecord(tenant1, drgKey, drgId, version);
    final var drg2 = sampleDecisionRequirementsRecord(tenant2, drgKey, drgId, version);
    final var decision1 = sampleDecisionRecord(tenant1, decisionKey, decisionId, version, drgKey);
    final var decision2 = sampleDecisionRecord(tenant2, decisionKey, decisionId, version, drgKey);
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);

    // when
    decisionState.storeDecisionRecord(decision1);
    decisionState.storeDecisionRecord(decision2);

    // then
    var actualDecision1 = decisionState.findDecisionByTenantAndKey(tenant1, decisionKey);
    var actualDecision2 = decisionState.findDecisionByTenantAndKey(tenant2, decisionKey);
    assertDecision(actualDecision1.get(), tenant1, decisionKey, decisionId, version, drgKey);
    assertDecision(actualDecision2.get(), tenant2, decisionKey, decisionId, version, drgKey);

    actualDecision1 =
        decisionState.findLatestDecisionByIdAndTenant(wrapString(decisionId), tenant1);
    actualDecision2 =
        decisionState.findLatestDecisionByIdAndTenant(wrapString(decisionId), tenant2);
    assertDecision(actualDecision1.get(), tenant1, decisionKey, decisionId, version, drgKey);
    assertDecision(actualDecision2.get(), tenant2, decisionKey, decisionId, version, drgKey);

    final var latestDecisions1 =
        decisionState.findDecisionsByTenantAndDecisionRequirementsKey(tenant1, drgKey);
    final var latestDecisions2 =
        decisionState.findDecisionsByTenantAndDecisionRequirementsKey(tenant2, drgKey);
    assertThat(latestDecisions1).hasSize(1);
    assertDecision(latestDecisions1.get(0), tenant1, decisionKey, decisionId, version, drgKey);
    assertThat(latestDecisions2).hasSize(1);
    assertDecision(latestDecisions2.get(0), tenant2, decisionKey, decisionId, version, drgKey);
  }

  @Test
  public void shouldStoreDecisionKeyByProcessIdAndDeploymentKeyForMultipleTenants() {
    // given
    final var drgKey = 123L;
    final var drgId = "drgId";
    final var decisionKey = 456L;
    final var decisionId = "decisionId";
    final int version = 1;
    final var tenant1 = "tenant1";
    final var tenant2 = "tenant2";
    final var deploymentKey = 789L;
    final var drg1 = sampleDecisionRequirementsRecord(tenant1, drgKey, drgId, version);
    final var drg2 = sampleDecisionRequirementsRecord(tenant2, drgKey, drgId, version);
    final var decision1 =
        sampleDecisionRecord(tenant1, decisionKey, decisionId, version, drgKey)
            .setDeploymentKey(deploymentKey);
    final var decision2 =
        sampleDecisionRecord(tenant2, decisionKey, decisionId, version, drgKey)
            .setDeploymentKey(deploymentKey);
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);
    decisionState.storeDecisionRecord(decision1);
    decisionState.storeDecisionRecord(decision2);

    // when
    decisionState.storeDecisionKeyByDecisionIdAndDeploymentKey(decision1);
    decisionState.storeDecisionKeyByDecisionIdAndDeploymentKey(decision2);

    // then
    final var actualDecision1 =
        decisionState.findDecisionByIdAndDeploymentKey(
            tenant1, wrapString(decisionId), deploymentKey);
    final var actualDecision2 =
        decisionState.findDecisionByIdAndDeploymentKey(
            tenant2, wrapString(decisionId), deploymentKey);
    assertDecision(
        actualDecision1.orElseThrow(), tenant1, decisionKey, decisionId, version, drgKey);
    assertDecision(
        actualDecision2.orElseThrow(), tenant2, decisionKey, decisionId, version, drgKey);
  }

  @Test
  public void shouldStoreDecisionKeyByProcessIdAndVersionTagForMultipleTenants() {
    // given
    final var drgKey = 123L;
    final var drgId = "drgId";
    final var decisionKey = 456L;
    final var decisionId = "decisionId";
    final int version = 1;
    final String versionTag = "v1.0";
    final var tenant1 = "tenant1";
    final var tenant2 = "tenant2";
    final var drg1 = sampleDecisionRequirementsRecord(tenant1, drgKey, drgId, version);
    final var drg2 = sampleDecisionRequirementsRecord(tenant2, drgKey, drgId, version);
    final var decision1 =
        sampleDecisionRecord(tenant1, decisionKey, decisionId, version, drgKey)
            .setVersionTag(versionTag);
    final var decision2 =
        sampleDecisionRecord(tenant2, decisionKey, decisionId, version, drgKey)
            .setVersionTag(versionTag);
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);
    decisionState.storeDecisionRecord(decision1);
    decisionState.storeDecisionRecord(decision2);

    // when
    decisionState.storeDecisionKeyByDecisionIdAndVersionTag(decision1);
    decisionState.storeDecisionKeyByDecisionIdAndVersionTag(decision2);

    // then
    final var actualDecision1 =
        decisionState.findDecisionByIdAndVersionTag(tenant1, wrapString(decisionId), versionTag);
    final var actualDecision2 =
        decisionState.findDecisionByIdAndVersionTag(tenant2, wrapString(decisionId), versionTag);
    assertDecision(
        actualDecision1.orElseThrow(), tenant1, decisionKey, decisionId, version, drgKey);
    assertDecision(
        actualDecision2.orElseThrow(), tenant2, decisionKey, decisionId, version, drgKey);
  }

  @Test
  void shouldNotPutDecisionForDrgBelongingToDifferentTenant() {
    // then
    final var drgKey = 123L;
    final var drgId = "drgId";
    final var decisionId = "decisionId";
    final int version = 1;
    final var tenant1 = "tenant1";
    final var tenant2 = "tenant2";
    final var drg = sampleDecisionRequirementsRecord(tenant1, drgKey, drgId, version);
    final var decision = sampleDecisionRecord(tenant2, drgKey, decisionId, version, drgKey);
    decisionState.storeDecisionRequirements(drg);

    // when then
    final var exception =
        assertThatExceptionOfType(ZeebeDbInconsistentException.class)
            .isThrownBy(() -> decisionState.storeDecisionRecord(decision))
            .actual();
    assertThat(exception)
        .hasMessage(
            """
                Foreign key DbTenantAwareKey[tenantKey=%s, wrappedKey=DbLong{%d}, placementType=PREFIX]\
                 does not exist in DMN_DECISION_REQUIREMENTS"""
                .formatted(tenant2, drgKey));
  }

  @Test
  void shouldDeleteDrgForTenant() {
    // given
    final var drgKey = 123L;
    final var drgId = "drgId";
    final int version = 1;
    final var tenant1 = "tenant1";
    final var tenant2 = "tenant2";
    final var drg1 = sampleDecisionRequirementsRecord(tenant1, drgKey, drgId, version);
    final var drg2 = sampleDecisionRequirementsRecord(tenant2, drgKey, drgId, version);
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);

    // when
    decisionState.deleteDecisionRequirements(drg1);

    // then
    final var actualDrg1 = decisionState.findDecisionRequirementsByTenantAndKey(tenant1, drgKey);
    final var actualDrg2 = decisionState.findDecisionRequirementsByTenantAndKey(tenant2, drgKey);
    assertThat(actualDrg1).describedAs("Tenant 1 is deleted from the state").isEmpty();
    assertThat(actualDrg2).describedAs("Tenant 2 is not removed from the state").isNotEmpty();
  }

  @Test
  void shouldDeleteDecisionForTenant() {
    // given
    final var drgKey = 123L;
    final var drgId = "drgId";
    final var decisionKey = 456L;
    final var decisionId = "decisionId";
    final int version = 1;
    final var tenant1 = "tenant1";
    final var tenant2 = "tenant2";
    final var drg1 = sampleDecisionRequirementsRecord(tenant1, drgKey, drgId, version);
    final var drg2 = sampleDecisionRequirementsRecord(tenant2, drgKey, drgId, version);
    final var decision1 = sampleDecisionRecord(tenant1, decisionKey, decisionId, version, drgKey);
    final var decision2 = sampleDecisionRecord(tenant2, decisionKey, decisionId, version, drgKey);
    decisionState.storeDecisionRequirements(drg1);
    decisionState.storeDecisionRequirements(drg2);
    decisionState.storeDecisionRecord(decision1);
    decisionState.storeDecisionRecord(decision2);

    // when
    decisionState.deleteDecision(decision1);

    // then
    final var actualDecision1 = decisionState.findDecisionByTenantAndKey(tenant1, decisionKey);
    final var actualDecision2 = decisionState.findDecisionByTenantAndKey(tenant2, decisionKey);
    assertThat(actualDecision1).describedAs("Tenant 1 is deleted from the state").isEmpty();
    assertThat(actualDecision2).describedAs("Tenant 2 is not removed from the state").isNotEmpty();
  }

  private DecisionRequirementsRecord sampleDecisionRequirementsRecord(
      final String tenantId, final long key, final String id, final int version) {
    return new DecisionRequirementsRecord()
        .setDecisionRequirementsId(id)
        .setDecisionRequirementsName("drg-name")
        .setDecisionRequirementsVersion(version)
        .setDecisionRequirementsKey(key)
        .setNamespace("namespace")
        .setResourceName("resource-name")
        .setChecksum(wrapString("checksum"))
        .setResource(wrapString("dmn-resource"))
        .setTenantId(tenantId);
  }

  private void assertDeployedDrg(
      final DeployedDrg drg,
      final String expectedTenant,
      final long expectedKey,
      final String expectedId,
      final int expectedVersion) {
    assertThat(drg)
        .extracting(
            DeployedDrg::getTenantId,
            DeployedDrg::getDecisionRequirementsKey,
            deployedDrg -> BufferUtil.bufferAsString(deployedDrg.getDecisionRequirementsId()),
            DeployedDrg::getDecisionRequirementsVersion)
        .describedAs("Gets correct DRG for tenant")
        .containsExactly(expectedTenant, expectedKey, expectedId, expectedVersion);
  }

  private DecisionRecord sampleDecisionRecord(
      final String tenantId,
      final long key,
      final String id,
      final int version,
      final long drgKey) {
    return new DecisionRecord()
        .setDecisionId(id)
        .setDecisionName("decision-name")
        .setVersion(version)
        .setDecisionKey(key)
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsKey(drgKey)
        .setTenantId(tenantId);
  }

  private void assertDecision(
      final PersistedDecision decision,
      final String expectedTenant,
      final long expectedKey,
      final String expectedId,
      final int expectedVersion,
      final long expectedDrgKey) {
    assertThat(decision)
        .extracting(
            PersistedDecision::getTenantId,
            PersistedDecision::getDecisionKey,
            deployedDrg -> BufferUtil.bufferAsString(deployedDrg.getDecisionId()),
            PersistedDecision::getVersion,
            PersistedDecision::getDecisionRequirementsKey)
        .describedAs("Gets correct decision for tenant")
        .containsExactly(expectedTenant, expectedKey, expectedId, expectedVersion, expectedDrgKey);
  }
}

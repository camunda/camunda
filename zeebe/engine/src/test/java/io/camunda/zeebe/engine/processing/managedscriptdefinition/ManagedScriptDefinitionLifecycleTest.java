/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.managedscriptdefinition;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.intent.ManagedScriptDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionRecordValue;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;

public final class ManagedScriptDefinitionLifecycleTest {

  private static final String RESOURCE_NAME = "script.js";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldLeaseCheckpointAndCompleteDefinition() {
    // given
    final long definitionKey = deployManagedScript();

    // when
    activate("worker-a", Duration.ofMinutes(2));

    // then
    final var leased =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.LEASED)
            .withManagedScriptDefinitionKey(definitionKey)
            .getFirst()
            .getValue();
    assertThat(leased.getRevision()).isEqualTo(1L);
    assertThat(leased.getProvider()).isEqualTo("fake");
    assertThat(leased.getLeaseOwner()).isEqualTo("worker-a");
    assertThat(leased.getLeaseToken()).isNotBlank();
    assertThat(leased.getLeaseExpiresAt()).isPositive();

    // when
    update(
        definitionKey,
        leased,
        ManagedScriptDefinitionStatus.DEPLOYING,
        "checkpoint-operation",
        "provider-operation",
        "");

    // then
    final var deploying =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.UPDATED)
            .withManagedScriptDefinitionKey(definitionKey)
            .withStatus(ManagedScriptDefinitionStatus.DEPLOYING)
            .getFirst()
            .getValue();
    assertThat(deploying.getRevision()).isEqualTo(2L);
    assertThat(deploying.getProviderOperationId()).isEqualTo("provider-operation");

    // when
    update(
        definitionKey,
        deploying,
        ManagedScriptDefinitionStatus.READY,
        "complete-operation",
        "",
        "provider-deployment");

    // then
    final var ready =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.UPDATED)
            .withManagedScriptDefinitionKey(definitionKey)
            .withStatus(ManagedScriptDefinitionStatus.READY)
            .getFirst()
            .getValue();
    assertThat(ready.getRevision()).isEqualTo(3L);
    assertThat(ready.getProviderDeploymentId()).isEqualTo("provider-deployment");
    assertThat(ready.getLeaseToken()).isEmpty();
    assertThat(ready.getLeaseExpiresAt()).isNegative();
  }

  @Test
  public void shouldRenewLeaseWithoutChangingDefinitionRevision() {
    // given
    final long definitionKey = deployManagedScript();
    activate("worker-a", Duration.ofSeconds(30));
    final var leased =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.LEASED)
            .withManagedScriptDefinitionKey(definitionKey)
            .getFirst()
            .getValue();
    final long originalExpiry = leased.getLeaseExpiresAt();
    engine.increaseTime(Duration.ofSeconds(10));

    // when
    final var renewal =
        new ManagedScriptDefinitionRecord()
            .setRevision(leased.getRevision())
            .setLeaseToken(leased.getLeaseToken())
            .setLeaseDuration(Duration.ofMinutes(2).toMillis());
    engine.writeCommandOnPartition(
        Protocol.DEPLOYMENT_PARTITION,
        definitionKey,
        ManagedScriptDefinitionIntent.RENEW_LEASE,
        renewal);

    // then
    final var renewed =
        RecordingExporter.managedScriptDefinitionRecords(
                ManagedScriptDefinitionIntent.LEASE_RENEWED)
            .withManagedScriptDefinitionKey(definitionKey)
            .getFirst()
            .getValue();
    assertThat(renewed.getRevision()).isEqualTo(leased.getRevision());
    assertThat(renewed.getLeaseExpiresAt()).isGreaterThan(originalExpiry);
  }

  @Test
  public void shouldReclaimExpiredLease() {
    // given
    final long definitionKey = deployManagedScript();
    activate("worker-a", Duration.ofSeconds(1));
    final var firstLease =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.LEASED)
            .withManagedScriptDefinitionKey(definitionKey)
            .getFirst()
            .getValue();
    engine.increaseTime(Duration.ofSeconds(2));

    // when
    activate("worker-b", Duration.ofMinutes(2));

    // then
    final var secondLease =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.LEASED)
            .withManagedScriptDefinitionKey(definitionKey)
            .skip(1)
            .getFirst()
            .getValue();
    assertThat(secondLease.getLeaseOwner()).isEqualTo("worker-b");
    assertThat(secondLease.getLeaseToken()).isNotEqualTo(firstLease.getLeaseToken());
    assertThat(secondLease.getRevision()).isEqualTo(2L);
  }

  private long deployManagedScript() {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .scriptTask(
                    "script",
                    task ->
                        task.zeebeJobType("io.camunda:managed-script:1")
                            .zeebeTaskHeader("language", "javascript")
                            .zeebeTaskHeader("runtime", "nodejs22")
                            .zeebeLinkedResources(
                                link ->
                                    link.resourceId(RESOURCE_NAME)
                                        .resourceType("ManagedScript")
                                        .linkName("script")
                                        .bindingType(ZeebeBindingType.deployment)))
                .endEvent()
                .done())
        .withJsonResource("return 1".getBytes(UTF_8), RESOURCE_NAME)
        .deploy();
    return RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.CREATED)
        .getFirst()
        .getKey();
  }

  private void activate(final String worker, final Duration leaseDuration) {
    final var request =
        new ManagedScriptDefinitionRecord()
            .setProvider("fake")
            .setLeaseOwner(worker)
            .setLeaseDuration(leaseDuration.toMillis())
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    engine.writeCommandOnPartition(
        Protocol.DEPLOYMENT_PARTITION, -1L, ManagedScriptDefinitionIntent.ACTIVATE, request);
  }

  private void update(
      final long definitionKey,
      final ManagedScriptDefinitionRecordValue current,
      final ManagedScriptDefinitionStatus status,
      final String operationId,
      final String providerOperationId,
      final String providerDeploymentId) {
    final var request =
        new ManagedScriptDefinitionRecord()
            .setRevision(current.getRevision())
            .setLeaseToken(current.getLeaseToken())
            .setStatus(status)
            .setOperationId(operationId)
            .setProviderOperationId(providerOperationId)
            .setProviderDeploymentId(providerDeploymentId);
    engine.writeCommandOnPartition(
        Protocol.DEPLOYMENT_PARTITION,
        definitionKey,
        ManagedScriptDefinitionIntent.UPDATE,
        request);
  }
}

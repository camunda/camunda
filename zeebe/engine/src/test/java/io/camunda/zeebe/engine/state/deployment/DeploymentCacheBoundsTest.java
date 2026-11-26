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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import java.time.Duration;
import java.time.InstantSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DeploymentCacheBoundsTest {

  private static final String TENANT_ID = "<default>";

  private MutableProcessingState processingState;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private DbProcessState lastProcessState;
  private String processTenantId;

  @Test
  void shouldReuseCachedFormWhileTtlHasNotElapsed() {
    // given
    final var formState = newFormState(new EngineConfiguration());
    storeForm(formState);

    // when
    final var first = formState.findLatestFormById("ttl-form", TENANT_ID).orElseThrow();
    final var second = formState.findLatestFormById("ttl-form", TENANT_ID).orElseThrow();

    // then
    assertThat(second).isSameAs(first);
  }

  @Test
  void shouldReloadFormWhenTtlHasElapsed() {
    // given
    final var formState = newFormState(expiringQuickly());
    storeForm(formState);

    // when
    final var first = formState.findLatestFormById("ttl-form", TENANT_ID).orElseThrow();

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(formState.findLatestFormById("ttl-form", TENANT_ID).orElseThrow())
                    .isNotSameAs(first)
                    .isEqualTo(first));
  }

  @Test
  void shouldReuseCachedDecisionRequirementsWhileTtlHasNotElapsed() {
    // given
    final var decisionState = newDecisionState(new EngineConfiguration());
    decisionState.storeDecisionRequirements(drgRecord());

    // when
    final var first = decisionState.findDecisionRequirementsByTenantAndKey(TENANT_ID, 1L);
    final var second = decisionState.findDecisionRequirementsByTenantAndKey(TENANT_ID, 1L);

    // then
    assertThat(second.orElseThrow()).isSameAs(first.orElseThrow());
  }

  @Test
  void shouldReloadDecisionRequirementsWhenTtlHasElapsed() {
    // given
    final var decisionState = newDecisionState(expiringQuickly());
    decisionState.storeDecisionRequirements(drgRecord());

    // when
    final var first =
        decisionState.findDecisionRequirementsByTenantAndKey(TENANT_ID, 1L).orElseThrow();

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(
                        decisionState
                            .findDecisionRequirementsByTenantAndKey(TENANT_ID, 1L)
                            .orElseThrow())
                    .isNotSameAs(first));
  }

  @Test
  void shouldReuseCachedResourceWhileTtlHasNotElapsed() {
    // given
    final var resourceState = newResourceState(new EngineConfiguration());
    storeResource(resourceState);

    // when
    final var first = resourceState.findLatestResourceById("ttl-resource", TENANT_ID).orElseThrow();
    final var second =
        resourceState.findLatestResourceById("ttl-resource", TENANT_ID).orElseThrow();

    // then
    assertThat(second).isSameAs(first);
  }

  @Test
  void shouldReloadResourceWhenTtlHasElapsed() {
    // given
    final var resourceState = newResourceState(expiringQuickly());
    storeResource(resourceState);

    // when
    final var first = resourceState.findLatestResourceById("ttl-resource", TENANT_ID).orElseThrow();

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(
                        resourceState
                            .findLatestResourceById("ttl-resource", TENANT_ID)
                            .orElseThrow())
                    .isNotSameAs(first));
  }

  @Test
  void shouldReuseCachedProcessWhileTtlHasNotElapsed() {
    // given
    final var key = storeProcess(new EngineConfiguration());

    // when
    final var first = lastProcessState.getProcessByKeyAndTenant(key, processTenantId);
    final var second = lastProcessState.getProcessByKeyAndTenant(key, processTenantId);

    // then
    assertThat(first).isNotNull();
    assertThat(second).isSameAs(first);
  }

  @Test
  void shouldReloadProcessWhenTtlHasElapsed() {
    // given
    final var key = storeProcess(expiringQuickly());

    // when
    final var first = lastProcessState.getProcessByKeyAndTenant(key, processTenantId);

    // then
    assertThat(first).isNotNull();
    await()
        .untilAsserted(
            () ->
                assertThat(lastProcessState.getProcessByKeyAndTenant(key, processTenantId))
                    .isNotSameAs(first));
  }

  @Test
  void shouldReloadVersionFromStateAfterEviction() {
    // given
    final var boundedManager =
        new VersionManager(0, zeebeDb, ZbColumnFamilies.PROCESS_VERSION, transactionContext, 1);
    final var otherManager =
        new VersionManager(0, zeebeDb, ZbColumnFamilies.PROCESS_VERSION, transactionContext, 1);
    boundedManager.addResourceVersion("evicted", 1, TENANT_ID);
    otherManager.addResourceVersion("evicted", 5, TENANT_ID);

    // when
    boundedManager.getLatestResourceVersion("other", TENANT_ID);

    // then
    assertThat(boundedManager.getLatestResourceVersion("evicted", TENANT_ID)).isEqualTo(5);
  }

  @Test
  void shouldKeepDatabaseReadFailureRecoverable() {
    // given
    final var readFailure = new ZeebeDbException("expected", new RuntimeException());
    final var failingContext =
        new TransactionContext() {
          @Override
          public void runInTransaction(final TransactionOperation operations) {
            throw readFailure;
          }

          @Override
          public ZeebeDbTransaction getCurrentTransaction() {
            throw readFailure;
          }
        };
    final var manager =
        new VersionManager(0, zeebeDb, ZbColumnFamilies.PROCESS_VERSION, failingContext, 1);

    // when / then
    assertThatThrownBy(() -> manager.getLatestResourceVersion("unknown", TENANT_ID))
        .isSameAs(readFailure);
  }

  private long storeProcess(final EngineConfiguration config) {
    lastProcessState =
        new DbProcessState(zeebeDb, transactionContext, config, InstantSource.system());
    final var processRecord = ProcessStateTest.creatingProcessRecord(processingState);
    processTenantId = processRecord.getTenantId();
    lastProcessState.putProcess(processRecord.getKey(), processRecord);
    return processRecord.getKey();
  }

  private static EngineConfiguration expiringQuickly() {
    return new EngineConfiguration().setDeploymentCacheTtl(Duration.ofMillis(1));
  }

  private DbFormState newFormState(final EngineConfiguration config) {
    return new DbFormState(zeebeDb, transactionContext, config);
  }

  private DbDecisionState newDecisionState(final EngineConfiguration config) {
    return new DbDecisionState(zeebeDb, transactionContext, config);
  }

  private DbResourceState newResourceState(final EngineConfiguration config) {
    return new DbResourceState(zeebeDb, transactionContext, config);
  }

  private void storeForm(final DbFormState formState) {
    final var record =
        new FormRecord()
            .setFormId("ttl-form")
            .setResourceName("form.form")
            .setVersion(1)
            .setFormKey(1L)
            .setTenantId(TENANT_ID);
    formState.storeFormInFormColumnFamily(record);
    formState.storeFormInFormByIdAndVersionColumnFamily(record);
    formState.updateLatestVersion(record);
  }

  private void storeResource(final DbResourceState resourceState) {
    final var record =
        new ResourceRecord()
            .setResourceId("ttl-resource")
            .setResourceName("resource-name")
            .setVersion(1)
            .setResourceKey(1L)
            .setDeploymentKey(1L)
            .setTenantId(TENANT_ID);
    resourceState.storeResourceInResourceColumnFamily(record);
    resourceState.storeResourceInResourceByIdAndVersionColumnFamily(record);
    resourceState.updateLatestVersion(record);
  }

  private DecisionRequirementsRecord drgRecord() {
    return new DecisionRequirementsRecord()
        .setDecisionRequirementsId("ttl-drg")
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

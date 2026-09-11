/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_8;

import static io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState.ACTIVE;
import static io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState.DRAINING;
import static io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState.PENDING_DELETION;
import static io.camunda.zeebe.engine.state.deployment.ProcessStateTest.creatingProcessRecord;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContextImpl;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class ReactivatePendingDeletionProcessesMigrationTest {

  private final ReactivatePendingDeletionProcessesMigration sut =
      new ReactivatePendingDeletionProcessesMigration();

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private MutableProcessingState processingState;
  private TransactionContext transactionContext;

  private MutableProcessState processState;

  // Raw views of the two column families that hold PersistedProcess, to seed the corruption exactly
  // as the wrap bug did (writing both) and to assert the on-disk state directly rather than through
  // the caches of the seeding MutableProcessState.
  private DbString tenantIdKey;
  private DbLong processDefinitionKey;
  private DbTenantAwareKey<DbLong> tenantAwareProcessDefinitionKey;
  private ColumnFamily<DbTenantAwareKey<DbLong>, PersistedProcess> processColumnFamily;
  private DbString processId;
  private DbLong processVersion;
  private DbTenantAwareKey<DbCompositeKey<DbString, DbLong>> tenantAwareProcessIdAndVersionKey;
  private ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>, PersistedProcess>
      processByIdAndVersionColumnFamily;

  @BeforeEach
  void setup() {
    processState = processingState.getProcessState();

    tenantIdKey = new DbString();
    processDefinitionKey = new DbLong();
    tenantAwareProcessDefinitionKey =
        new DbTenantAwareKey<>(tenantIdKey, processDefinitionKey, PlacementType.PREFIX);
    processColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE,
            transactionContext,
            tenantAwareProcessDefinitionKey,
            new PersistedProcess());

    processId = new DbString();
    processVersion = new DbLong();
    final var idAndVersionKey = new DbCompositeKey<>(processId, processVersion);
    tenantAwareProcessIdAndVersionKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionKey, PlacementType.PREFIX);
    processByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
            transactionContext,
            tenantAwareProcessIdAndVersionKey,
            new PersistedProcess());
  }

  @Test
  void shouldReactivateProcessStuckInPendingDeletion() {
    // given — the wrap bug persisted PENDING_DELETION into both column families
    final var record = deployProcess("stuck");
    corruptToPendingDeletion(record);
    assertStateInBothColumnFamilies(record, PENDING_DELETION);

    // when
    runMigration();

    // then
    assertStateInBothColumnFamilies(record, ACTIVE);
  }

  @Test
  void shouldLeaveActiveProcessUntouched() {
    // given
    final var record = deployProcess("active");
    assertProcessCacheState(record, ACTIVE);

    // when
    runMigration();

    // then
    assertProcessCacheState(record, ACTIVE);
  }

  @Test
  void shouldLeaveDrainingProcessUntouched() {
    // given — DRAINING is a legitimate resting state that must not be reactivated
    final var record = deployProcess("draining");
    processState.updateProcessState(record, DRAINING);
    assertProcessCacheState(record, DRAINING);

    // when
    runMigration();

    // then
    assertProcessCacheState(record, DRAINING);
  }

  @Test
  void shouldOnlyReactivatePendingDeletionAmongMixedStates() {
    // given
    final var active = deployProcess("mixed");
    final var pending = deployProcess("mixed");
    final var draining = deployProcess("mixed");
    corruptToPendingDeletion(pending);
    processState.updateProcessState(draining, DRAINING);

    // when
    runMigration();

    // then
    assertProcessCacheState(active, ACTIVE);
    assertStateInBothColumnFamilies(pending, ACTIVE);
    assertProcessCacheState(draining, DRAINING);
  }

  @Test
  void shouldReactivatePerTenantWithoutTouchingOtherTenants() {
    // given — same bpmnProcessId and version under two non-default tenants; tenant-a is stuck in
    // PENDING_DELETION while tenant-b is DRAINING (a non-ACTIVE state that must be preserved), so a
    // wrong write against tenant-a would surface as tenant-b flipping to ACTIVE.
    final var tenantA = deployProcess("shared", "tenant-a");
    final var tenantB = deployProcess("shared", "tenant-b");
    corruptToPendingDeletion(tenantA);
    processState.updateProcessState(tenantB, DRAINING);
    assertStateInBothColumnFamilies(tenantA, PENDING_DELETION);
    assertProcessCacheState(tenantB, DRAINING);

    // when
    runMigration();

    // then — only tenant-a is reactivated; tenant-b stays DRAINING
    assertStateInBothColumnFamilies(tenantA, ACTIVE);
    assertProcessCacheState(tenantB, DRAINING);
  }

  @Test
  void shouldBeIdempotentWhenRunTwice() {
    // given
    final var record = deployProcess("twice");
    corruptToPendingDeletion(record);

    // when
    runMigration();
    runMigration();

    // then
    assertStateInBothColumnFamilies(record, ACTIVE);
  }

  private ProcessRecord deployProcess(final String bpmnProcessId) {
    final var record = creatingProcessRecord(processingState, bpmnProcessId);
    processState.putProcess(record.getKey(), record);
    return record;
  }

  private ProcessRecord deployProcess(final String bpmnProcessId, final String tenantId) {
    final var record =
        creatingProcessRecord(processingState, bpmnProcessId, 1).setTenantId(tenantId);
    processState.putProcess(record.getKey(), record);
    return record;
  }

  /** Reproduces the wrap bug: overwrite both column families with a PENDING_DELETION copy. */
  private void corruptToPendingDeletion(final ProcessRecord record) {
    final var corrupted = new PersistedProcess();
    corrupted.wrap(record, record.getKey());
    corrupted.setState(PENDING_DELETION);

    tenantIdKey.wrapString(record.getTenantId());
    processDefinitionKey.wrapLong(record.getKey());
    processColumnFamily.update(tenantAwareProcessDefinitionKey, corrupted);

    processId.wrapString(record.getBpmnProcessId());
    processVersion.wrapLong(record.getVersion());
    processByIdAndVersionColumnFamily.update(tenantAwareProcessIdAndVersionKey, corrupted);
  }

  private void runMigration() {
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    assertThat(sut.needsToRun(context)).isTrue();
    sut.runMigration(context);
  }

  private void assertProcessCacheState(
      final ProcessRecord record, final PersistedProcessState expectedState) {
    tenantIdKey.wrapString(record.getTenantId());
    processDefinitionKey.wrapLong(record.getKey());
    assertThat(processColumnFamily.get(tenantAwareProcessDefinitionKey).getState())
        .as("state in PROCESS_CACHE")
        .isEqualTo(expectedState);
  }

  private void assertStateInBothColumnFamilies(
      final ProcessRecord record, final PersistedProcessState expectedState) {
    assertProcessCacheState(record, expectedState);

    processId.wrapString(record.getBpmnProcessId());
    processVersion.wrapLong(record.getVersion());
    assertThat(processByIdAndVersionColumnFamily.get(tenantAwareProcessIdAndVersionKey).getState())
        .as("state in PROCESS_CACHE_BY_ID_AND_VERSION")
        .isEqualTo(expectedState);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs the process state contract against the RocksDB state, plus the tests of what is specific to
 * its column families.
 */
@ExtendWith(ProcessingStateExtension.class)
public final class ProcessStateTest extends AbstractProcessStateContractTest {

  private MutableProcessingState processingState;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;

  @Override
  protected MutableProcessState createProcessState() {
    return processingState.getProcessState();
  }

  @Override
  protected TransactionContext transactionContext() {
    return transactionContext;
  }

  /**
   * RocksDB keeps a copy of each process by id and version, which a state change of the process
   * doesn't update. The relational state has no such copy.
   */
  @Test
  public void shouldRestoreTheByVersionCopyOfAStrayPendingDeletionProcess() {
    // given - the write bug left PENDING_DELETION in both the by-key and the by-version copy
    final long processDefinitionKey = 100L;
    final var processRecord = creatingProcessRecord().setKey(processDefinitionKey);
    processState.putProcess(processDefinitionKey, processRecord);
    processState.updateProcessState(processRecord, PersistedProcessState.PENDING_DELETION);

    final var tenantId = new DbString();
    final var processId = new DbString();
    final var version = new DbLong();
    final var tenantAwareKey =
        new DbTenantAwareKey<>(
            tenantId, new DbCompositeKey<>(processId, version), PlacementType.PREFIX);
    final ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>, PersistedProcess>
        byVersion =
            zeebeDb.createColumnFamily(
                ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
                transactionContext,
                tenantAwareKey,
                new PersistedProcess());
    tenantId.wrapString(processRecord.getTenantId());
    processId.wrapString(processRecord.getBpmnProcessId());
    version.wrapLong(processRecord.getVersion());
    final var stale = byVersion.get(tenantAwareKey);
    stale.setState(PersistedProcessState.PENDING_DELETION);
    byVersion.update(tenantAwareKey, stale);

    // when
    processState.restoreFromPendingDeletion(processRecord.getTenantId(), processDefinitionKey);

    // then
    assertThat(byVersion.get(tenantAwareKey).getState()).isEqualTo(PersistedProcessState.ACTIVE);
  }

  /** A process record for tests on the processing state, see {@link #newProcessRecord}. */
  public static ProcessRecord creatingProcessRecord(
      final MutableProcessingState processingState,
      final String processId,
      final int version,
      final String versionTag) {
    return newProcessRecord(
        processingState.getKeyGenerator()::nextKey, processId, version, versionTag);
  }
}

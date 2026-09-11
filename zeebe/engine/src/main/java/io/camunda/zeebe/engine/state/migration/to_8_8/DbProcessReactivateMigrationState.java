/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_8;

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
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repairs process definitions left durably in {@link PersistedProcessState#PENDING_DELETION} by the
 * {@code PersistedProcess.wrap} bug (see #62820; fixed in #59319). That bug reused a single shared
 * {@link PersistedProcess} instance without resetting its {@code state}, so a delete followed by a
 * later deploy could stamp {@code PENDING_DELETION} onto a brand-new, unrelated version and persist
 * it to RocksDB.
 *
 * <p>A committed {@code PENDING_DELETION} at rest is always such an artifact: real deletion writes
 * DELETING+DELETED in a single atomic batch, so the transient state is never observable between
 * commands, and draining rests in the separate {@link PersistedProcessState#DRAINING} state. The
 * repair therefore promotes every resting {@code PENDING_DELETION} back to {@link
 * PersistedProcessState#ACTIVE} and leaves {@code DRAINING} untouched.
 */
public final class DbProcessReactivateMigrationState {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DbProcessReactivateMigrationState.class);

  private final DbString tenantIdKey;

  private final DbLong processDefinitionKey;
  private final DbTenantAwareKey<DbLong> tenantAwareProcessDefinitionKey;

  /** [tenant id | process definition key] => process */
  private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedProcess> processColumnFamily;

  private final DbString processId;
  private final DbLong processVersion;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>
      tenantAwareProcessIdAndVersionKey;

  /** [tenant id | process id | process version] => process */
  private final ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>, PersistedProcess>
      processByIdAndVersionColumnFamily;

  public DbProcessReactivateMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
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
    idAndVersionKey = new DbCompositeKey<>(processId, processVersion);
    tenantAwareProcessIdAndVersionKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionKey, PlacementType.PREFIX);
    processByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
            transactionContext,
            tenantAwareProcessIdAndVersionKey,
            new PersistedProcess());
  }

  public int reactivatePendingDeletionProcesses() {
    // Collect first, then write: avoid mutating the column family while iterating it, and avoid
    // retaining the shared PersistedProcess instance that forEach hands back.
    final List<PendingProcess> pending = new ArrayList<>();
    processColumnFamily.forEach(
        (key, process) -> {
          if (process.getState() == PersistedProcessState.PENDING_DELETION) {
            pending.add(
                new PendingProcess(
                    process.getTenantId(),
                    process.getKey(),
                    BufferUtil.bufferAsString(process.getBpmnProcessId()),
                    process.getVersion()));
          }
        });

    for (final var entry : pending) {
      tenantIdKey.wrapString(entry.tenantId());
      processDefinitionKey.wrapLong(entry.processDefinitionKey());

      final var process = processColumnFamily.get(tenantAwareProcessDefinitionKey);
      process.setState(PersistedProcessState.ACTIVE);

      // Both column families hold a copy of the process; keep them consistent, as persistProcess
      // writes both. PROCESS_CACHE_BY_ID_AND_VERSION backs latest-version / by-id-and-version
      // resolution; PROCESS_CACHE backs lookup by process definition key.
      processColumnFamily.update(tenantAwareProcessDefinitionKey, process);

      processId.wrapString(entry.bpmnProcessId());
      processVersion.wrapLong(entry.version());
      processByIdAndVersionColumnFamily.update(tenantAwareProcessIdAndVersionKey, process);
    }

    LOGGER.info(
        "Reactivated {} process definitions from PENDING_DELETION to ACTIVE", pending.size());

    return pending.size();
  }

  private record PendingProcess(
      String tenantId, long processDefinitionKey, String bpmnProcessId, long version) {}
}

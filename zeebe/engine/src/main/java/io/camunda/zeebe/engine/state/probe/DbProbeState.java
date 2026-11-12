/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.probe;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.mutable.MutableProbeState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import java.util.function.BiPredicate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DbProbeState implements MutableProbeState {

  private final DbLong probeKey = new DbLong();
  private final PersistedProbe persistedProbe = new PersistedProbe();
  private final ColumnFamily<DbLong, PersistedProbe> probesColumnFamily;

  // [[probeId | probeKey] | tenantId]: the probes a poll of the probe ID may collect
  private final DbString activatableProbeId = new DbString();
  private final DbString activatableTenantId = new DbString();
  private final DbLong activatableProbeKey = new DbLong();
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>> activatableKey;
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>>, DbNil>
      activatableColumnFamily;

  // [deadline | probeKey]: the probes in deadline order, for the time-out scheduler
  private final DbLong deadline = new DbLong();
  private final DbLong deadlineProbeKey = new DbLong();
  private final DbCompositeKey<DbLong, DbForeignKey<DbLong>> deadlineKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbForeignKey<DbLong>>, DbNil>
      deadlinesColumnFamily;

  public DbProbeState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    probesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROBES, transactionContext, probeKey, persistedProbe);

    activatableKey =
        new DbTenantAwareKey<>(
            activatableTenantId,
            new DbCompositeKey<>(
                activatableProbeId,
                new DbForeignKey<>(activatableProbeKey, ZbColumnFamilies.PROBES)),
            PlacementType.SUFFIX);
    activatableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROBES_ACTIVATABLE,
            transactionContext,
            activatableKey,
            DbNil.INSTANCE);

    deadlineKey =
        new DbCompositeKey<>(
            deadline, new DbForeignKey<>(deadlineProbeKey, ZbColumnFamilies.PROBES));
    deadlinesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROBE_DEADLINES, transactionContext, deadlineKey, DbNil.INSTANCE);
  }

  @Override
  public void create(final long key, final ProbeRecord probe) {
    probeKey.wrapLong(key);
    persistedProbe.reset();
    persistedProbe.setState(State.ACTIVATABLE).setRecord(probe);
    probesColumnFamily.insert(probeKey, persistedProbe);

    wrapActivatableKey(key, probe);
    activatableColumnFamily.insert(activatableKey, DbNil.INSTANCE);

    wrapDeadlineKey(key, probe);
    deadlinesColumnFamily.insert(deadlineKey, DbNil.INSTANCE);
  }

  @Override
  public void remove(final long key) {
    probeKey.wrapLong(key);
    final var probe = probesColumnFamily.get(probeKey);
    if (probe == null) {
      return;
    }
    final var record = probe.getRecord();
    wrapActivatableKey(key, record);
    activatableColumnFamily.deleteIfExists(activatableKey);
    wrapDeadlineKey(key, record);
    deadlinesColumnFamily.deleteExisting(deadlineKey);
    probesColumnFamily.deleteExisting(probeKey);
  }

  @Override
  public @Nullable ProbeRecord getProbe(final long key) {
    probeKey.wrapLong(key);
    final var probe = probesColumnFamily.get(probeKey);
    return probe == null ? null : new ProbeRecord().copyFrom(probe.getRecord());
  }

  @Override
  public State getState(final long key) {
    probeKey.wrapLong(key);
    final var probe = probesColumnFamily.get(probeKey);
    return probe == null ? State.NOT_FOUND : probe.getState();
  }

  @Override
  public long forEachTimedOutProbe(
      final long timestamp, final BiPredicate<Long, ProbeRecord> visitor) {
    final long[] nextDeadline = {-1L};
    deadlinesColumnFamily.whileTrue(
        (key, nil) -> {
          final long probeDeadline = key.first().getValue();
          if (probeDeadline > timestamp) {
            nextDeadline[0] = probeDeadline;
            return false;
          }
          final long timedOutKey = key.second().inner().getValue();
          final var probe = getProbe(timedOutKey);
          if (probe != null && !visitor.test(timedOutKey, probe)) {
            nextDeadline[0] = probeDeadline;
            return false;
          }
          return true;
        });
    return nextDeadline[0];
  }

  private void wrapActivatableKey(final long key, final ProbeRecord probe) {
    activatableProbeId.wrapBuffer(probe.getProbeIdBuffer());
    activatableProbeKey.wrapLong(key);
    activatableTenantId.wrapBuffer(probe.getTenantIdBuffer());
  }

  private void wrapDeadlineKey(final long key, final ProbeRecord probe) {
    deadline.wrapLong(probe.getDeadline());
    deadlineProbeKey.wrapLong(key);
  }
}

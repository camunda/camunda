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
import io.camunda.zeebe.engine.state.mutable.MutableProbeState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public final class DbProbeState implements MutableProbeState {

  private final DbLong probeKey;
  private final DbForeignKey<DbLong> fkProbe;
  private final PersistedProbe persistedProbe = new PersistedProbe();
  private final ColumnFamily<DbLong, PersistedProbe> probesColumnFamily;

  private final DbLong deadline;
  private final DbCompositeKey<DbLong, DbForeignKey<DbLong>> deadlineProbeKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbForeignKey<DbLong>>, DbNil>
      deadlinesColumnFamily;

  public DbProbeState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    probeKey = new DbLong();
    fkProbe = new DbForeignKey<>(probeKey, ZbColumnFamilies.PROBES);
    probesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROBES, transactionContext, probeKey, persistedProbe);

    deadline = new DbLong();
    deadlineProbeKey = new DbCompositeKey<>(deadline, fkProbe);
    deadlinesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROBE_DEADLINES, transactionContext, deadlineProbeKey, DbNil.INSTANCE);
  }

  @Override
  public void create(final long key, final ProbeRecord record) {
    probeKey.wrapLong(key);
    persistedProbe.reset();
    persistedProbe.setState(State.CREATED).setRecord(record);
    probesColumnFamily.insert(probeKey, persistedProbe);
    deadline.wrapLong(record.getDeadline());
    deadlinesColumnFamily.insert(deadlineProbeKey, DbNil.INSTANCE);
  }

  @Override
  public void activate(final long key, final String worker) {
    update(key, State.ACTIVATED, probe -> probe.getRecord().setWorker(worker));
  }

  @Override
  public void yield(final long key) {
    update(
        key,
        State.CREATED,
        probe -> probe.getRecord().setYieldCount(probe.getRecord().getYieldCount() + 1));
  }

  @Override
  public void park(final long key) {
    update(key, State.PARKED, probe -> {});
  }

  @Override
  public void remove(final long key) {
    probeKey.wrapLong(key);
    final var probe = probesColumnFamily.get(probeKey);
    if (probe == null) {
      return;
    }
    deadline.wrapLong(probe.getRecord().getDeadline());
    deadlinesColumnFamily.deleteIfExists(deadlineProbeKey);
    probesColumnFamily.deleteExisting(probeKey);
  }

  @Override
  public ProbeRecord getProbe(final long key) {
    probeKey.wrapLong(key);
    final var probe = probesColumnFamily.get(probeKey);
    return probe == null ? null : probe.getRecord().copy();
  }

  @Override
  public State getState(final long key) {
    probeKey.wrapLong(key);
    final var probe = probesColumnFamily.get(probeKey);
    return probe == null ? State.NOT_FOUND : probe.getState();
  }

  @Override
  public long findTimedOutProbes(
      final long timestamp, final BiPredicate<Long, ProbeRecord> visitor) {
    final long[] nextDeadline = {-1L};
    deadlinesColumnFamily.whileTrue(
        (key, nil) -> {
          final long probeDeadline = key.first().getValue();
          if (probeDeadline > timestamp) {
            nextDeadline[0] = probeDeadline;
            return false;
          }
          final long key2 = key.second().inner().getValue();
          final var record = getProbe(key2);
          if (record != null && !visitor.test(key2, record)) {
            nextDeadline[0] = probeDeadline;
            return false;
          }
          return true;
        });
    return nextDeadline[0];
  }

  private void update(final long key, final State state, final Consumer<PersistedProbe> change) {
    probeKey.wrapLong(key);
    final var probe = probesColumnFamily.get(probeKey);
    if (probe == null) {
      return;
    }
    change.accept(probe);
    probe.setState(state);
    probesColumnFamily.update(probeKey, probe);
  }
}

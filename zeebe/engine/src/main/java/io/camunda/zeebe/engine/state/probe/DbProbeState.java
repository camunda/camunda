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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;
import org.agrona.DirectBuffer;
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

  // [probeKey | [storeId | secretReference]] and [[storeId | secretReference] | probeKey]: what a
  // parked probe waits for, and which probes wait for a reference
  private final DbLong parkedProbeKey = new DbLong();
  private final DbForeignKey<DbLong> fkParkedProbe =
      new DbForeignKey<>(parkedProbeKey, ZbColumnFamilies.PROBES);
  private final DbString parkedStoreId = new DbString();
  private final DbString parkedSecretReference = new DbString();
  private final DbCompositeKey<DbString, DbString> parkedReference =
      new DbCompositeKey<>(parkedStoreId, parkedSecretReference);
  private final DbCompositeKey<DbForeignKey<DbLong>, DbCompositeKey<DbString, DbString>>
      referenceByProbeKey = new DbCompositeKey<>(fkParkedProbe, parkedReference);
  private final ColumnFamily<
          DbCompositeKey<DbForeignKey<DbLong>, DbCompositeKey<DbString, DbString>>, DbNil>
      secretReferencesByProbeColumnFamily;
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbForeignKey<DbLong>>
      probeByReferenceKey = new DbCompositeKey<>(parkedReference, fkParkedProbe);
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbString>, DbForeignKey<DbLong>>, DbNil>
      probesBySecretReferenceColumnFamily;

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

    secretReferencesByProbeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.SECRET_REFERENCES_BY_PROBE,
            transactionContext,
            referenceByProbeKey,
            DbNil.INSTANCE);
    probesBySecretReferenceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROBES_BY_SECRET_REFERENCE,
            transactionContext,
            probeByReferenceKey,
            DbNil.INSTANCE);
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
  public void activate(final long key, final DirectBuffer worker) {
    probeKey.wrapLong(key);
    final var probe = probesColumnFamily.get(probeKey);
    if (probe == null) {
      return;
    }
    wrapActivatableKey(key, probe.getRecord());
    activatableColumnFamily.deleteExisting(activatableKey);
    probe.setState(State.ACTIVATED).getRecord().setWorker(worker);
    probesColumnFamily.update(probeKey, probe);
  }

  @Override
  public void makeActivatable(final long key) {
    probeKey.wrapLong(key);
    final var probe = probesColumnFamily.get(probeKey);
    if (probe == null) {
      return;
    }
    probe.setState(State.ACTIVATABLE).getRecord().setWorker("");
    probesColumnFamily.update(probeKey, probe);
    wrapActivatableKey(key, probe.getRecord());
    activatableColumnFamily.insert(activatableKey, DbNil.INSTANCE);
    unpark(key);
  }

  @Override
  public void park(final long key, final String storeId, final String secretReference) {
    probeKey.wrapLong(key);
    final var probe = probesColumnFamily.get(probeKey);
    if (probe == null) {
      return;
    }
    // a probe parked on several references leaves the activatable index with the first one
    wrapActivatableKey(key, probe.getRecord());
    activatableColumnFamily.deleteIfExists(activatableKey);
    probe.setState(State.WAITING_FOR_SECRET_RESOLUTION);
    probesColumnFamily.update(probeKey, probe);

    parkedProbeKey.wrapLong(key);
    parkedStoreId.wrapString(storeId);
    parkedSecretReference.wrapString(secretReference);
    secretReferencesByProbeColumnFamily.upsert(referenceByProbeKey, DbNil.INSTANCE);
    probesBySecretReferenceColumnFamily.upsert(probeByReferenceKey, DbNil.INSTANCE);
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
    // before the probe itself: the parking entries hold a foreign key to it
    unpark(key);
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

  @Override
  public void forEachActivatableProbe(
      final DirectBuffer probeId,
      final List<String> tenantIds,
      final BiPredicate<Long, ProbeRecord> visitor) {
    activatableProbeId.wrapBuffer(probeId);
    activatableColumnFamily.whileEqualPrefix(
        activatableProbeId,
        (key, nil) -> {
          if (!tenantIds.contains(key.tenantKey().toString())) {
            return true;
          }
          final long activatableKey = key.wrappedKey().second().inner().getValue();
          final var probe = getProbe(activatableKey);
          return probe == null || visitor.test(activatableKey, probe);
        });
  }

  @Override
  public void forEachProbeParkedOn(
      final String storeId, final String secretReference, final LongPredicate visitor) {
    parkedStoreId.wrapString(storeId);
    parkedSecretReference.wrapString(secretReference);
    probesBySecretReferenceColumnFamily.whileEqualPrefix(
        parkedReference,
        (key, nil) -> {
          return visitor.test(key.second().inner().getValue());
        });
  }

  @Override
  public void forEachSecretReferenceParkedOn(
      final long key, final BiPredicate<String, String> visitor) {
    parkedProbeKey.wrapLong(key);
    secretReferencesByProbeColumnFamily.whileEqualPrefix(
        fkParkedProbe,
        (entry, nil) -> {
          final var reference = entry.second();
          return visitor.test(reference.first().toString(), reference.second().toString());
        });
  }

  /** Removes the parking entries of the probe, if it has any. */
  private void unpark(final long key) {
    // collected first: deleting while iterating would rewrap the key being iterated
    final List<String[]> references = new ArrayList<>();
    forEachSecretReferenceParkedOn(
        key, (storeId, secretReference) -> references.add(new String[] {storeId, secretReference}));
    for (final String[] reference : references) {
      parkedProbeKey.wrapLong(key);
      parkedStoreId.wrapString(reference[0]);
      parkedSecretReference.wrapString(reference[1]);
      secretReferencesByProbeColumnFamily.deleteExisting(referenceByProbeKey);
      probesBySecretReferenceColumnFamily.deleteExisting(probeByReferenceKey);
    }
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

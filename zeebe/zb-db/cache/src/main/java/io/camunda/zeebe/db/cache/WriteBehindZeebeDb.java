/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ZeebeDb} that keeps committed state in a write-behind cache and writes it to the backend
 * it wraps in the background. It only uses the backend through the {@link ZeebeDb} API, so it works
 * with any backend.
 */
final class WriteBehindZeebeDb<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDb<ColumnFamilyNames>, HealthMonitorable {

  private final ZeebeDb<ColumnFamilyNames> backend;
  private final WriteBehindStore<ColumnFamilyNames> store;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final Set<FailureListener> failureListeners = new CopyOnWriteArraySet<>();
  private volatile @Nullable Instant failedAt;

  WriteBehindZeebeDb(
      final ZeebeDb<ColumnFamilyNames> backend,
      final WriteBehindConfiguration configuration,
      final ConsistencyChecksSettings consistencyChecksSettings) {
    this.backend = backend;
    this.consistencyChecksSettings = consistencyChecksSettings;
    store = new WriteBehindStore<>(backend, configuration);
    store.addFailureListener(this::reportFailure);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyNames columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    return new CachedColumnFamily<>(
        store,
        cachedContext(context),
        columnFamily,
        keyInstance,
        valueInstance,
        consistencyChecksSettings);
  }

  /**
   * Waits until the backend holds every transaction committed so far, then snapshots the backend
   * while the writer keeps writing. Each backend transaction is a group of whole transactions, so
   * the snapshot holds whole transactions, at or past everything committed before this call.
   */
  @Override
  public void createSnapshot(final File snapshotDir) {
    store.catchUp();
    backend.createSnapshot(snapshotDir);
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return backend.getProperty(propertyName);
  }

  @Override
  public TransactionContext createContext() {
    return new CachedTransactionContext<>(store, backend);
  }

  @Override
  public boolean isEmpty(final ColumnFamilyNames column, final TransactionContext context) {
    return createColumnFamily(column, context, new RawKey(), new RawValue()).isEmpty();
  }

  @Override
  public String componentName() {
    return "WriteBehindZeebeDb";
  }

  /** Healthy until the store fails, then dead: the partition has to be rebuilt. */
  @Override
  public HealthReport getHealthReport() {
    final var cause = store.failure();
    if (cause == null) {
      return HealthReport.healthy(this);
    }
    return HealthReport.dead(this)
        .withIssue(cause, Objects.requireNonNullElseGet(failedAt, Instant::now));
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    failureListeners.add(failureListener);
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    failureListeners.remove(failureListener);
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return backend.getMeterRegistry();
  }

  @Override
  public void exportMetrics() {
    backend.exportMetrics();
  }

  /** Closes the cache first, so that its writer doesn't touch the backend once it is closed. */
  @Override
  public void close() throws Exception {
    try {
      store.close();
    } finally {
      backend.close();
    }
  }

  private void reportFailure(final Throwable cause) {
    failedAt = Instant.now();
    final var report = getHealthReport();
    failureListeners.forEach(listener -> listener.onUnrecoverableFailure(report));
  }

  @SuppressWarnings("unchecked") // every CachedTransactionContext is created by createContext
  private CachedTransactionContext<ColumnFamilyNames> cachedContext(
      final TransactionContext context) {
    if (context instanceof final CachedTransactionContext<?> cached) {
      return (CachedTransactionContext<ColumnFamilyNames>) cached;
    }
    throw new IllegalArgumentException(
        "Expected a transaction context of the write-behind cache, but got " + context);
  }
}

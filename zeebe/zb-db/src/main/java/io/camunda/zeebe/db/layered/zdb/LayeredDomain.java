/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.SnapshotSource;
import io.camunda.zeebe.db.layered.ViewPublisher;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * One single-owner durability domain of a {@link LayeredZeebeDb}: its own owner-thread {@link
 * #context()}, its own set of layered stores (a column family joins the domain by being created on
 * the domain's context — see {@link LayeredZeebeDb#createColumnFamily}), its own lazily built
 * {@link #coordinator()} with an independent persist cadence, and its own {@link #viewPublisher()}
 * fed by that coordinator.
 *
 * <p><b>No cross-domain atomicity:</b> each domain drains its persist rounds through its own inner
 * transaction, so the rounds of two domains commit independently — deliberately matching the
 * semantics separate consumers (engine vs. exporters) already have on the wrapped database. State
 * that must be atomic with a recovery anchor must live in a single domain.
 *
 * <p><b>Lifecycle:</b> create all of the domain's layered column families before the first {@link
 * #coordinator()} call — the coordinator captures the domain's store set, so later creations in
 * this domain throw (other domains are unaffected).
 *
 * <p><b>Threading:</b> a domain is owner-thread only, but different domains may have different
 * owner threads — domains share nothing except the wrapped database. Views cross threads through
 * the domain's {@link ViewPublisher}.
 */
public final class LayeredDomain {

  private final String name;
  private final Map<String, LayeredKeyValueStore> storesByColumnFamily = new LinkedHashMap<>();
  private final Map<String, ColumnFamily<DbBytes, DbBytes>> persistColumnFamilies = new HashMap<>();
  private final Map<String, ColumnFamily<DbBytes, DbBytes>> snapshotColumnFamilies =
      new HashMap<>();
  private final LayeredTransactionContext context;
  private final TransactionContext delegateReadContext;
  private final TransactionContext persistContext;
  private final TransactionContext snapshotReadContext;
  private final SnapshotSource snapshotSource;
  private final InnerPersistSink sink;
  private final ViewPublisher viewPublisher = new ViewPublisher();

  private LayeredStoreCoordinator coordinator;

  /**
   * @param snapshotReadContext the dedicated context of the unpinned fallback source; must be
   *     non-null exactly when {@code sharedSnapshotSource} is null
   * @param sharedSnapshotSource the pinning source shared across domains (store names are globally
   *     unique because a column family has one owning domain), or null for the fallback
   */
  LayeredDomain(
      final String name,
      final TransactionContext delegateReadContext,
      final TransactionContext persistContext,
      final TransactionContext snapshotReadContext,
      final SnapshotSource sharedSnapshotSource) {
    this.name = Objects.requireNonNull(name, "name");
    this.delegateReadContext = Objects.requireNonNull(delegateReadContext, "delegateReadContext");
    this.persistContext = Objects.requireNonNull(persistContext, "persistContext");
    this.snapshotReadContext = snapshotReadContext;
    if (sharedSnapshotSource == null) {
      Objects.requireNonNull(snapshotReadContext, "snapshotReadContext");
      snapshotSource = new UnpinnedSnapshotSource(snapshotColumnFamilies);
    } else {
      snapshotSource = sharedSnapshotSource;
    }
    context = new LayeredTransactionContext(storesByColumnFamily.values());
    sink = new InnerPersistSink(persistContext, persistColumnFamilies);
  }

  /** The unique name of this domain within its {@link LayeredZeebeDb}. */
  public String name() {
    return name;
  }

  /**
   * The single owner-thread context of this domain; every call returns the same instance. Column
   * families created on it buffer writes in memory until one of this domain's persist rounds drains
   * them.
   */
  public TransactionContext context() {
    return context;
  }

  /**
   * The coordinator driving freezes and persist rounds over this domain's layered stores. Built
   * lazily on the first call, capturing every layered column family the domain holds so far —
   * create them all first. Every published view also reaches {@link #viewPublisher()}.
   */
  public LayeredStoreCoordinator coordinator() {
    if (coordinator == null) {
      coordinator =
          new LayeredStoreCoordinator(
              storesByColumnFamily.values(), sink, snapshotSource, viewPublisher::publish);
    }
    return coordinator;
  }

  /**
   * The coordinator driving this domain's freezes and persist rounds, with the given listener
   * receiving every published {@link ReadOnlyView} in addition to {@link #viewPublisher()}. Must be
   * the domain's first {@code coordinator} call.
   */
  public LayeredStoreCoordinator coordinator(final Consumer<ReadOnlyView> viewListener) {
    Objects.requireNonNull(viewListener, "viewListener");
    if (coordinator != null) {
      throw new IllegalStateException(
          ("expected the view listener of domain '%s' to be registered before the coordinator is"
                  + " built, but one already exists")
              .formatted(name));
    }
    coordinator =
        new LayeredStoreCoordinator(
            storesByColumnFamily.values(),
            sink,
            snapshotSource,
            view -> {
              viewPublisher.publish(view);
              viewListener.accept(view);
            });
    return coordinator;
  }

  /**
   * The distribution point handing this domain's {@link ReadOnlyView}s to concurrent readers. Views
   * flow once the domain's coordinator is built (which publishes the initial one).
   */
  public ViewPublisher viewPublisher() {
    return viewPublisher;
  }

  /**
   * Whether any of this domain's stores' pinned (un-evictable) entries exceed its byte budget — the
   * signal to schedule one of this domain's persist rounds now.
   */
  public boolean overCapacity() {
    return storesByColumnFamily.values().stream().anyMatch(LayeredKeyValueStore::overCapacity);
  }

  // ------------------------------------------------------------------
  // Wiring accessors for LayeredZeebeDb
  // ------------------------------------------------------------------

  LayeredTransactionContext transactionContext() {
    return context;
  }

  Map<String, LayeredKeyValueStore> stores() {
    return storesByColumnFamily;
  }

  Map<String, ColumnFamily<DbBytes, DbBytes>> persistColumnFamilies() {
    return persistColumnFamilies;
  }

  Map<String, ColumnFamily<DbBytes, DbBytes>> snapshotColumnFamilies() {
    return snapshotColumnFamilies;
  }

  TransactionContext delegateReadContext() {
    return delegateReadContext;
  }

  TransactionContext persistContext() {
    return persistContext;
  }

  TransactionContext snapshotReadContext() {
    return snapshotReadContext;
  }

  boolean coordinatorBuilt() {
    return coordinator != null;
  }

  /** Releases the coordinator's view reference on shutdown; idempotent. */
  void closeCoordinatorIfBuilt() {
    if (coordinator != null) {
      coordinator.close();
    }
  }
}

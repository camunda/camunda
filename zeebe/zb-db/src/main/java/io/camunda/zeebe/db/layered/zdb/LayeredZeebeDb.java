/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator;
import io.camunda.zeebe.db.layered.LayeredStoreMetrics;
import io.camunda.zeebe.db.layered.SnapshotSource;
import io.camunda.zeebe.db.layered.typed.LayeredColumnFamily;
import io.camunda.zeebe.protocol.EnumValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A decorator making the layered state store usable through the existing {@link ZeebeDb} surface,
 * wrapping a real database (the {@code inner} durable store) and hosting an ownership registry of
 * single-owner {@link LayeredDomain}s.
 *
 * <p><b>Pass-through by default:</b> every context created via {@link #createContext()} — and every
 * column family created on such a context — behaves exactly like the wrapped database: secondary
 * consumers (exporters writing through their own context, the query service, migrations, backup)
 * are untouched by the layering. Their committed writes are visible to the layered paths through
 * their delegate reads.
 *
 * <p><b>The layered path is explicit and per domain:</b> {@link #registerDomain(String)} creates a
 * named {@link LayeredDomain} with its own owner-thread context, layered stores, lazily built
 * {@link LayeredStoreCoordinator} (independent persist cadence) and view publisher. A column family
 * joins a domain implicitly by being created on that domain's context; a column family has at most
 * one owning domain — creating it in a second domain throws. Transaction commit on a domain's
 * context promotes the staged batch in memory — <em>no RocksDB write ever happens on commit</em> —
 * and rollback discards exactly the staged batch. Buffered state reaches the wrapped database only
 * in a domain's atomic persist rounds; rounds of different domains are separate inner transactions,
 * so <b>cross-domain atomicity is intentionally not provided</b> (see {@link LayeredDomain}).
 *
 * <p><b>Default domain:</b> {@link #layeredContext()} and {@link #defaultDomain()} are conveniences
 * over a default domain named {@value #DEFAULT_DOMAIN_NAME}, registered on first use —
 * single-domain wirings need never touch the registry.
 *
 * <p><b>Lifecycle:</b> create all of a domain's layered column families before that domain's first
 * {@code coordinator()} call — the coordinator captures the store set, so creating a <em>new</em>
 * layered column family in that domain afterwards throws (re-creating an existing one binds fresh
 * accessors to the same store and is allowed; successor owners on a reused database rely on it).
 * {@link #close()} releases every built coordinator's view reference and closes the wrapped
 * database.
 *
 * <p><b>Snapshot source:</b> by default views published by a domain's coordinator read the wrapped
 * database <em>without</em> a pinned snapshot (see {@link UnpinnedSnapshotSource} for the exact
 * limitations); wirings with asynchronous readers must inject a pinning {@link SnapshotSource} via
 * the dedicated constructor — it is shared by all domains, which is sound because a column family
 * (and thus a store name) has exactly one owning domain.
 *
 * <p><b>Threading:</b> the ownership registry — domain registration/lookup and the column-family
 * ownership check — is thread-safe: different domains have different owner threads (e.g. the stream
 * processor and exporter director actors of one partition), and a domain may register while another
 * thread resolves its context or creates a pass-through column family. Everything <em>inside</em> a
 * domain stays owner-thread-only: its context, stores and coordinator must only ever be used by the
 * single thread owning that domain (see {@link LayeredDomain}).
 */
public final class LayeredZeebeDb<ColumnFamilyType extends Enum<ColumnFamilyType> & EnumValue>
    implements ZeebeDb<ColumnFamilyType> {

  /** The name of the domain backing the {@link #layeredContext()} convenience surface. */
  public static final String DEFAULT_DOMAIN_NAME = "engine";

  private final ZeebeDb<ColumnFamilyType> inner;
  private final LayeredZeebeDbConfig config;
  private final SnapshotSource snapshotSource;
  private final boolean fallbackSnapshots;

  // guards domainsByName: registration happens on each domain's owner thread (engine and exporter
  // actors), while lookups (domainOf, close) may run on other threads concurrently — registration
  // is cold-path, so the lock is uncontended in steady state
  private final Object domainRegistrationLock = new Object();
  private final Map<String, LayeredDomain> domainsByName = new LinkedHashMap<>();
  private final Map<String, LayeredDomain> ownerByColumnFamily = new ConcurrentHashMap<>();

  /** Creates a facade whose coordinator views read through the unpinned fallback source. */
  public LayeredZeebeDb(final ZeebeDb<ColumnFamilyType> inner, final LayeredZeebeDbConfig config) {
    this(inner, config, null);
  }

  /**
   * Creates a facade whose coordinator views read through the given pinning snapshot source; the
   * caller owns the source's lifecycle.
   */
  public LayeredZeebeDb(
      final ZeebeDb<ColumnFamilyType> inner,
      final LayeredZeebeDbConfig config,
      final SnapshotSource snapshotSource) {
    this.inner = Objects.requireNonNull(inner, "inner");
    this.config = Objects.requireNonNull(config, "config");
    this.snapshotSource = snapshotSource;
    fallbackSnapshots = snapshotSource == null;
  }

  // ------------------------------------------------------------------
  // ZeebeDb surface
  // ------------------------------------------------------------------

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    final LayeredDomain domain = domainOf(context);
    if (domain == null) {
      return inner.createColumnFamily(columnFamily, context, keyInstance, valueInstance);
    }
    final String name = columnFamily.name();
    final LayeredDomain owner = ownerByColumnFamily.putIfAbsent(name, domain);
    if (owner != null && owner != domain) {
      throw new IllegalStateException(
          ("expected column family '%s' to have a single owning domain, but it is owned by '%s'"
                  + " and was requested in '%s'")
              .formatted(name, owner.name(), domain.name()));
    }
    LayeredKeyValueStore store = domain.stores().get(name);
    if (store == null) {
      // only creating a NEW store is forbidden after the coordinator captured the store set;
      // re-creating a column family over an existing store is fine — a successor owner (e.g. a
      // new stream processor on a reused database) binds fresh accessors to the same stores
      if (domain.coordinatorBuilt()) {
        throw new IllegalStateException(
            ("expected all layered column families of domain '%s' to be created before its"
                    + " coordinator, but '%s' was requested after coordinator() was called")
                .formatted(domain.name(), name));
      }
      store = domain.stores().computeIfAbsent(name, ignored -> createStore(columnFamily, domain));
    }
    return new LayeredTransactionalColumnFamily<>(
        domain.transactionContext(), new LayeredColumnFamily<>(store, keyInstance, valueInstance));
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    inner.createSnapshot(snapshotDir);
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return inner.getProperty(propertyName);
  }

  @Override
  public TransactionContext createContext() {
    return inner.createContext();
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    final LayeredDomain domain = domainOf(context);
    if (domain == null) {
      return inner.isEmpty(column, context);
    }
    final LayeredKeyValueStore store = domain.stores().get(column.name());
    if (store == null) {
      // no layered store means no buffered writes; the wrapped database alone answers
      return inner.isEmpty(column, domain.delegateReadContext());
    }
    return new LayeredColumnFamily<>(store, new DbBytes(), new DbBytes()).isEmpty();
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return inner.getMeterRegistry();
  }

  @Override
  public void exportMetrics() {
    inner.exportMetrics();
  }

  @Override
  public void close() throws Exception {
    final List<LayeredDomain> domains;
    synchronized (domainRegistrationLock) {
      domains = List.copyOf(domainsByName.values());
    }
    domains.forEach(LayeredDomain::closeCoordinatorIfBuilt);
    inner.close();
  }

  // ------------------------------------------------------------------
  // Ownership registry (not part of the ZeebeDb interface)
  // ------------------------------------------------------------------

  /**
   * Registers a new single-owner domain under the given unique name. Register a domain before
   * creating its column families (they join by being created on {@link LayeredDomain#context()})
   * and thus before its coordinator is built. Thread-safe (see the class javadoc's Threading
   * section); the returned domain itself is owner-thread-only.
   *
   * @throws IllegalStateException if a domain with that name is already registered
   */
  public LayeredDomain registerDomain(final String name) {
    Objects.requireNonNull(name, "name");
    synchronized (domainRegistrationLock) {
      if (domainsByName.containsKey(name)) {
        throw new IllegalStateException(
            "expected a unique domain name, but '%s' is already registered".formatted(name));
      }
      final LayeredDomain domain =
          new LayeredDomain(
              name,
              inner.createContext(),
              inner.createContext(),
              fallbackSnapshots ? inner.createContext() : null,
              snapshotSource,
              LayeredStoreMetrics.of(inner.getMeterRegistry(), name));
      domainsByName.put(name, domain);
      return domain;
    }
  }

  /**
   * The owner-thread context of the default {@value #DEFAULT_DOMAIN_NAME} domain, registered on
   * first use; every call returns the same instance. Convenience over {@link
   * #registerDomain(String)} for single-domain wirings.
   */
  public TransactionContext layeredContext() {
    return defaultDomain().context();
  }

  /** The configuration all of this facade's layered stores were created with. */
  public LayeredZeebeDbConfig config() {
    return config;
  }

  // ------------------------------------------------------------------
  // Wiring
  // ------------------------------------------------------------------

  /**
   * The domain backing the {@link #layeredContext()} convenience surface, registered on first use;
   * every call returns the same instance.
   */
  public LayeredDomain defaultDomain() {
    return domain(DEFAULT_DOMAIN_NAME);
  }

  /**
   * The domain with the given name, registered on first use; every call returns the same instance.
   * The get-or-register semantics are what successive owners of a reused database rely on (e.g. a
   * new exporter director after a leader transition): the first owner registers the domain, every
   * successor gets it back with its stores — and any buffered state — intact. Thread-safe (see the
   * class javadoc's Threading section); the returned domain itself is owner-thread-only.
   */
  public LayeredDomain domain(final String name) {
    synchronized (domainRegistrationLock) {
      final LayeredDomain existing = domainsByName.get(name);
      return existing != null ? existing : registerDomain(name);
    }
  }

  private LayeredDomain domainOf(final TransactionContext context) {
    synchronized (domainRegistrationLock) {
      for (final LayeredDomain domain : domainsByName.values()) {
        if (domain.context() == context) {
          return domain;
        }
      }
      return null;
    }
  }

  private LayeredKeyValueStore createStore(
      final ColumnFamilyType columnFamily, final LayeredDomain domain) {
    final String name = columnFamily.name();
    final ColumnFamily<DbBytes, DbBytes> readColumnFamily =
        inner.createColumnFamily(
            columnFamily, domain.delegateReadContext(), new DbBytes(), new DbBytes());
    final ColumnFamily<DbBytes, DbBytes> persistColumnFamily =
        inner.createColumnFamily(
            columnFamily, domain.persistContext(), new DbBytes(), new DbBytes());
    domain.persistColumnFamilies().put(name, persistColumnFamily);
    if (fallbackSnapshots) {
      domain
          .snapshotColumnFamilies()
          .put(
              name,
              inner.createColumnFamily(
                  columnFamily, domain.snapshotReadContext(), new DbBytes(), new DbBytes()));
    }
    return new LayeredKeyValueStore(
        name,
        new InnerBytesStore(readColumnFamily, persistColumnFamily, domain.persistContext()),
        config.maxBytesPerStore(),
        config.absorbDeletes(),
        config.pipelineSegmentLimit(),
        domain.metrics());
  }
}

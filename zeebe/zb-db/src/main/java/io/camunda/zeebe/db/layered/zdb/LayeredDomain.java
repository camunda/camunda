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
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.MergeRound;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.LayeredStoreMetrics;
import io.camunda.zeebe.db.layered.PersistTrigger;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.SnapshotSource;
import io.camunda.zeebe.db.layered.ViewPublisher;
import io.camunda.zeebe.db.layered.segment.ChunkPool;
import io.camunda.zeebe.db.layered.segment.ChunkWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One single-owner durability domain of a {@link LayeredZeebeDb}: its own owner-thread {@link
 * #context()}, its own set of layered stores (a column family joins the domain by being created on
 * the domain's context — see {@link LayeredZeebeDb#createColumnFamily}), its own lazily built
 * {@link #coordinator()} with independently triggered persist rounds, and its own {@link
 * #viewPublisher()} fed by that coordinator.
 *
 * <p><b>No cross-domain atomicity:</b> each domain drains its persist rounds through its own inner
 * transaction, so the rounds of two domains commit independently — deliberately matching the
 * semantics separate consumers (engine vs. exporters) already have on the wrapped database. State
 * that must be atomic with a recovery anchor must live in a single domain.
 *
 * <p><b>Lifecycle:</b> create all of the domain's layered column families before the first {@link
 * #coordinator()} call — the coordinator captures the domain's store set, so creating a new column
 * family in this domain afterwards throws (other domains are unaffected; re-creating an existing
 * one is allowed).
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
  private final LayeredStoreMetrics metrics;
  private final ViewPublisher viewPublisher;
  private final ChunkWriter chunkWriter;

  private LayeredStoreCoordinator coordinator;
  // anchor-entry designations made before the coordinator exists, applied when it is built
  private final Map<String, byte[]> pendingAnchorEntries = new LinkedHashMap<>();

  /**
   * @param snapshotReadContext the dedicated context of the unpinned fallback source; must be
   *     non-null exactly when {@code sharedSnapshotSource} is null
   * @param sharedSnapshotSource the pinning source shared across domains (store names are globally
   *     unique because a column family has one owning domain), or null for the fallback
   * @param chunkPool the database-wide pool freeze chunks come from and retire to; the domain owns
   *     one writer over it (all of a domain's stores freeze on the domain's owner thread, so small
   *     segments pack into shared chunks)
   * @param metrics receives this domain's instrumentation; never null (use the no-op)
   */
  LayeredDomain(
      final String name,
      final TransactionContext delegateReadContext,
      final TransactionContext persistContext,
      final TransactionContext snapshotReadContext,
      final SnapshotSource sharedSnapshotSource,
      final ChunkPool chunkPool,
      final LayeredStoreMetrics metrics) {
    chunkWriter = new ChunkWriter(Objects.requireNonNull(chunkPool, "chunkPool"));
    this.name = Objects.requireNonNull(name, "name");
    this.delegateReadContext = Objects.requireNonNull(delegateReadContext, "delegateReadContext");
    this.persistContext = Objects.requireNonNull(persistContext, "persistContext");
    this.snapshotReadContext = snapshotReadContext;
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    if (sharedSnapshotSource == null) {
      Objects.requireNonNull(snapshotReadContext, "snapshotReadContext");
      snapshotSource = new UnpinnedSnapshotSource(snapshotColumnFamilies);
    } else {
      snapshotSource = sharedSnapshotSource;
    }
    context = new LayeredTransactionContext(storesByColumnFamily.values());
    sink = new InnerPersistSink(persistContext, persistColumnFamilies);
    viewPublisher = new ViewPublisher(metrics);
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
              storesByColumnFamily.values(), sink, snapshotSource, viewPublisher::publish, metrics);
      pendingAnchorEntries.forEach(coordinator::designateAnchorEntry);
      pendingAnchorEntries.clear();
      metrics.registerStoreGauges(storesByColumnFamily.values());
    }
    return coordinator;
  }

  /**
   * Designates the entry {@code columnFamilyName}/{@code key} as the recovery anchor's carrier: a
   * paced drain defers it to a round's final slice so no partial slice can ever commit an anchor
   * ahead of the state it describes (see {@link
   * LayeredStoreCoordinator#designateAnchorEntry(String, byte[])}). In this wiring the anchor is a
   * normal drained key — e.g. the engine's last-processed position — not a separate sink cell (see
   * {@link InnerPersistSink}). Owner thread; may be called before the coordinator is built (the
   * designation is applied when it is), and re-designating on a reused database overwrites.
   */
  public void designateAnchorEntry(final String columnFamilyName, final byte[] key) {
    if (coordinator != null) {
      coordinator.designateAnchorEntry(columnFamilyName, key);
    } else {
      pendingAnchorEntries.put(
          Objects.requireNonNull(columnFamilyName, "columnFamilyName"),
          Objects.requireNonNull(key, "key").clone());
    }
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

  /**
   * Approximate heap footprint of all buffered (not yet persisted) writes across this domain's
   * stores — what a persist round would drain, including the bytes captured by an in-flight round
   * until it completes (they are still pinned heap). Compared against the buffer-pressure ladder's
   * rungs of a total byte budget by the runtime to trigger size-based rounds; contrast with {@link
   * #overCapacity()}, which is a per-store budget.
   */
  public long bufferedBytes() {
    long bufferedBytes = 0;
    for (final LayeredKeyValueStore store : storesByColumnFamily.values()) {
      bufferedBytes += store.bufferedBytes();
    }
    return bufferedBytes;
  }

  /**
   * Counts a batch boundary at which this domain's {@link #bufferedBytes()} had reached the full
   * buffered-bytes budget — the buffer-pressure ladder's top rung, surfaced as a meter because no
   * admission slow-down seam into the log stream's flow control exists yet (the runtime driving the
   * domain logs and counts instead of throttling).
   */
  public void countAdmissionPressure() {
    metrics.countAdmissionPressure();
  }

  /**
   * Whether any of this domain's stores holds committed writes in its active overlay — writes a
   * {@link #freezeNow(long)} would make visible to read views.
   */
  public boolean hasActiveWrites() {
    for (final LayeredKeyValueStore store : storesByColumnFamily.values()) {
      if (store.hasActiveWrites()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Freezes every store's active overlay into a segment stamped {@code watermark} and republishes
   * this domain's read view, making everything committed up to the watermark visible to view
   * readers. Cheap — pointer swaps and flattens, no durable IO. Owner thread only; must not be
   * called while {@link #batchInFlight()} is true (staging must be empty on a freeze).
   *
   * @param watermark the highest log position whose effects the active overlays contain
   */
  public void freezeNow(final long watermark) {
    coordinator().freezeAll(watermark);
  }

  /** Whether any of this domain's stores holds buffered writes not yet persisted. */
  public boolean hasBufferedWrites() {
    for (final LayeredKeyValueStore store : storesByColumnFamily.values()) {
      if (store.hasBufferedWrites()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether a batch is currently open on this domain's owner-thread context (a transaction has
   * started but was not yet committed or rolled back). Persist rounds must not run while a batch is
   * in flight — its staging writes are not part of any durable cut yet.
   */
  public boolean batchInFlight() {
    return context.transactionOpen();
  }

  /**
   * Discards a batch a previous owner left open — e.g. a stream processor that closed mid-batch
   * before a successor took over the same database. The staged writes were never part of a
   * committed batch, so dropping them loses nothing the log does not replay; committed layers are
   * untouched. A no-op when no batch is in flight.
   */
  public void discardOpenBatch() {
    if (!context.transactionOpen()) {
      return;
    }
    try {
      context.getCurrentTransaction().rollback();
    } catch (final Exception e) {
      // the layered rollback is a pure in-memory discard and cannot actually fail; the checked
      // exception is an artifact of the shared ZeebeDbTransaction interface
      throw new ZeebeDbException(
          "Failed to discard the open batch of domain '%s'".formatted(name), e);
    }
  }

  /**
   * Runs one full persist round inline on the owner thread: prepare, drain into the wrapped
   * database in one atomic batch, complete. Everything committed on this domain's context up to
   * {@code watermark} — including the recovery anchor written through a layered column family —
   * moves to the wrapped database as one prefix-consistent cut.
   *
   * <p>Must not be called while {@link #batchInFlight()} is true or a round is outstanding.
   *
   * @param watermark the highest log position whose effects the buffered state contains
   * @param trigger why this round runs, for instrumentation
   * @throws ZeebeDbException if the drain fails; the segments stay buffered and the next round
   *     retries them
   */
  public void persistNow(final long watermark, final PersistTrigger trigger) {
    final PersistRound round = preparePersist(watermark, trigger);
    try {
      round.persist();
    } catch (final Exception e) {
      completePersist(round, false);
      throw new ZeebeDbException(
          "Failed to persist the buffered state of domain '%s'".formatted(name), e);
    }
    completePersist(round, true);
  }

  /**
   * Prepares one persist round on the owner thread (freeze plus segment capture, cheap) and returns
   * it for draining. {@link PersistRound#persist()} may then run on an IO thread — it touches only
   * the captured immutable segments and the sink — while the owner thread keeps writing, reading
   * and freezing; the captured segments stay in their pipelines, readable and shadowing the wrapped
   * database, until {@link #completePersist(PersistRound, boolean)} retires them back on the owner
   * thread. Rounds are single-flight per domain.
   *
   * <p>Must not be called while {@link #batchInFlight()} is true or a round is outstanding.
   *
   * @param watermark the highest log position whose effects the buffered state contains
   * @param trigger why this round runs, for instrumentation
   */
  public PersistRound preparePersist(final long watermark, final PersistTrigger trigger) {
    final LayeredStoreCoordinator roundCoordinator = coordinator();
    metrics.countRound(trigger);
    return roundCoordinator.prepareRound(watermark);
  }

  /**
   * Finishes a prepared round on the owner thread after {@link PersistRound#persist()} returned
   * (successfully or not): on success the drained segments retire and the read view rotates; on
   * failure they stay buffered and the next round retries them. Safe to call while a batch is in
   * flight — completion never touches the staging layer's contents.
   */
  public void completePersist(final PersistRound round, final boolean success) {
    coordinator().completeRound(round, success);
  }

  /** Whether one of this domain's persist rounds is outstanding (prepared but not completed). */
  public boolean roundInFlight() {
    return coordinator != null && coordinator.roundOutstanding();
  }

  /**
   * Captures a merge round over every store whose pipeline needs merging, or returns null when no
   * store does. {@link MergeRound#merge()} may then run on an IO thread — index-only over the
   * captured immutable segments — while the owner thread keeps writing, reading and freezing;
   * complete on the owner thread via {@link #completeMerge(MergeRound, boolean)}. Merges are
   * single-flight per domain and may overlap an outstanding persist round, but a new round must not
   * start while a merge is outstanding (see {@link LayeredStoreCoordinator}).
   */
  public @Nullable MergeRound prepareMerge() {
    return coordinator().prepareMerge();
  }

  /**
   * Finishes a prepared merge on the owner thread after {@link MergeRound#merge()} returned
   * (successfully or not): on success the merged segments replace their captured runs; on failure
   * the runs stay as they were and the next merge retries them.
   */
  public void completeMerge(final MergeRound round, final boolean success) {
    coordinator().completeMerge(round, success);
  }

  /** Whether one of this domain's merges is outstanding (prepared but not completed). */
  public boolean mergeInFlight() {
    return coordinator != null && coordinator.mergeOutstanding();
  }

  /**
   * Completes (as failed) a merge a previous owner left outstanding — the merge analog of {@link
   * #abortStaleRound()}, with the same precondition (the previous owner's IO must have terminated).
   * The captured runs stay in their pipelines, unmerged; the successor's merges retry them. A no-op
   * when no merge is outstanding.
   */
  public void abortStaleMerge() {
    if (coordinator != null) {
      coordinator.abortOutstandingMerge();
    }
  }

  /**
   * Completes (as failed) a persist round a previous owner left outstanding — e.g. a stream
   * processor that died between preparing a round and completing it before a successor took over
   * the same database. The captured segments stay buffered and the successor's rounds retry them;
   * see {@link LayeredStoreCoordinator#abortOutstandingRound()} for the idempotency argument and
   * the precondition (the previous owner's persist IO must have terminated). A no-op when no round
   * is outstanding.
   */
  public void abortStaleRound() {
    if (coordinator != null) {
      coordinator.abortOutstandingRound();
    }
  }

  /**
   * Completes (by draining it) a persist round a previous owner left outstanding — the successor's
   * recovery step for domains whose drains are <em>paced</em>: the orphaned round may have
   * committed partial slices without the anchor, and re-draining it to completion (idempotent,
   * newest-wins, anchor in the final slice) restores the durable anchor invariant before the
   * successor starts processing. Contrast with {@link #abortStaleRound()}, which remains the right
   * recovery for domains whose drains are all-or-nothing single batches (e.g. the exporter
   * director's inline {@link #persistNow(long, PersistTrigger)}). Runs the drain inline on the
   * calling owner thread; a no-op when no round is outstanding. Same precondition as {@link
   * #abortStaleRound()}: the previous owner's persist IO must have terminated.
   *
   * @throws ZeebeDbException if the re-drain fails; the round is completed as failed instead — its
   *     segments stay buffered, keep masking the partial slices from layered readers, and the
   *     successor's next round retries them, so the caller may continue
   */
  public void completeStaleRoundForward() {
    if (coordinator == null || !coordinator.roundOutstanding()) {
      return;
    }
    try {
      coordinator.completeOutstandingRoundForward();
    } catch (final Exception e) {
      throw new ZeebeDbException(
          ("Failed to complete the persist round a previous owner of domain '%s' left"
                  + " outstanding; its segments stay buffered and the next round retries them")
              .formatted(name),
          e);
    }
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

  ChunkWriter chunkWriter() {
    return chunkWriter;
  }

  LayeredStoreMetrics metrics() {
    return metrics;
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

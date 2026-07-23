/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The translator's working memory: the <b>open</b> entities only. Rows enter on activation and
 * leave on completion/termination (evict-after-emit); everything durable about <em>finished</em>
 * entities lives in the lake, not here.
 *
 * <p>Durability contract: mutations may be applied per record and committed in batches; the store
 * does not persist consumed offsets — the lake's snapshot summary is the single offset authority
 * ({@code LakeWriter#committedOffset}). Replaying records from the last lake-committed offset
 * against this state must converge (all operations are last-write-wins puts and idempotent
 * deletes), so no atomic coupling between state and lake is required.
 *
 * @see io.camunda.analytics.lake.write.LakeWriter
 */
public interface TranslatorState extends AutoCloseable {

  /** An open process instance, keyed by its process instance key. */
  record OpenInstance(
      long processDefinitionKey, String processId, int version, String tenantId, long startMs) {}

  /**
   * An open element instance, keyed by its element instance key.
   *
   * @param instanceStartMs the owning instance's start ({@code OpenInstance#startMs}) — the family
   *     date activities will be partitioned and retired by; must be the LAST component.
   */
  record OpenElement(
      long instanceKey,
      String processId,
      int version,
      String tenantId,
      String elementId,
      String elementType,
      long startMs,
      long instanceStartMs) {}

  /** Whether a variant-k1 name-map entry names a BPMN element or a sequence flow. */
  enum VariantElementKind {
    ELEMENT,
    FLOW
  }

  /**
   * The variant-k1 accumulator for one <b>open</b> process instance — one entry per open instance,
   * evicted the moment the instance completes (see {@code
   * io.camunda.analytics.lake.translate.LakeTranslator}'s "Variant capture (scheme variant-k1)"
   * javadoc section for the full scheme this backs).
   *
   * @param lastPosition the Zeebe {@code position} of the last record folded into this accumulator
   *     — the per-accumulator replay guard (see the scheme javadoc's "REPLAY GUARD" paragraph);
   *     {@code -1} before any element/flow has folded
   * @param hash the running XOR-folded variant hash; read (never recomputed) at completion
   * @param count number of distinct element/flow ids folded so far — always equal to {@code
   *     seenHashes.length}
   * @param seenHashes sorted (ascending) truncated 32-bit id hashes already folded, one per
   *     distinct element/flow id seen so far (set semantics: a repeat contributes no second entry)
   */
  record VariantAccumulator(long lastPosition, long hash, int count, int[] seenHashes) {}

  /**
   * One entry of the per-process element/flow name map: which BPMN id (and whether it names an
   * element or a sequence flow) a given process's truncated 32-bit id hash resolves to. Written on
   * first sight of a given {@code (bpmnProcessId, h32)} pair (see {@code LakeTranslator}'s own
   * heap-cache javadoc for why writes are rare in practice), read back at instance completion to
   * decode a finished accumulator's {@code seenHashes} into id strings.
   */
  record VariantName(String id, VariantElementKind kind) {}

  /**
   * A sequence flow's source/target element ids, resolved once from its process definition's
   * deployed BPMN (see {@code io.camunda.analytics.lake.translate.LakeTranslator}'s own {@code
   * ValueType.PROCESS}/{@code CREATED} handling) and persisted so branch-count folding can resolve
   * them by id lookup alone, with no BPMN model kept in memory and no adjacency guessed from record
   * ordering (unreliable under parallel-gateway interleaving).
   */
  record FlowEndpoints(String sourceElementId, String targetElementId) {}

  /**
   * One object sighting accumulated for an open instance — see {@code
   * io.camunda.analytics.lake.translate.LakeTranslator}'s "Object fabric capture" javadoc section.
   *
   * @param scopeKey the Zeebe scope key the sighting occurred at; equal to the owning instance key
   *     for a root-scope sighting (there is no separate boolean — root-ness is always derived by
   *     comparing this against the instance key the sighting is stored under)
   */
  record ObjectSighting(String objectType, String objectId, long scopeKey) {}

  /**
   * The compact list of {@link ObjectSighting}s accumulated so far for one open instance, capped at
   * a documented maximum (see {@code LakeTranslator#MAX_OBJECT_SIGHTINGS_PER_INSTANCE}'s own
   * javadoc) — {@code overflowed} records that the cap was hit, so further sightings for this
   * instance are silently dropped from the list (not from the {@code objects} dictionary table,
   * which is uncapped) rather than growing it unbounded.
   */
  record ObjectSightingList(List<ObjectSighting> sightings, boolean overflowed) {

    public ObjectSightingList {
      sightings = List.copyOf(sightings);
    }
  }

  /**
   * Whether a distinct object's lifecycle accumulator is still open or has been closed and left as
   * a tombstone — see {@code io.camunda.analytics.lake.translate.LakeTranslator}'s "Object
   * lifecycle capture" javadoc section. Never any other value: v1 has no interim states between
   * birth and close.
   */
  enum LifecycleStatus {
    OPEN,
    CLOSED_TOMBSTONE
  }

  /**
   * How an object's birth timestamp was determined — v1 has exactly one source (see {@code
   * LakeTranslator}'s "Object lifecycle capture" javadoc section); kept as an enum, not a bare
   * constant, so a later v2 birth qualifier (e.g. a declared "created" event distinct from mere
   * sighting) is an additive enum constant, not a wire-format break.
   */
  enum BirthQualifier {
    FIRST_SIGHTING
  }

  /**
   * One distinct object's lifecycle accumulator — one entry per (object type, object id) ever
   * sighted, created the moment it is first sighted and never deleted (a closed object becomes a
   * {@link LifecycleStatus#CLOSED_TOMBSTONE} in place; see {@code LakeTranslator}'s own javadoc
   * section for the full scheme and {@code RocksDbTranslatorState}'s own key-encoding javadoc for
   * why {@code (objectType, objectId)} needs a length prefix on only the first component).
   *
   * @param nSightings count of distinct (instance, scope) sightings accepted into the object's
   *     lifetime CF-7 relations-derivation lists while OPEN (see {@code
   *     LakeTranslator#recordObjectSightingForRelations}'s own return value) — never incremented
   *     for a duplicate, and frozen once {@code status} becomes {@link
   *     LifecycleStatus#CLOSED_TOMBSTONE}
   * @param closedAtMs only meaningful when {@code status} is {@link
   *     LifecycleStatus#CLOSED_TOMBSTONE}; {@code 0} while {@link LifecycleStatus#OPEN}
   */
  record ObjectLifecycle(
      LifecycleStatus status,
      long birthTsMs,
      BirthQualifier birthQualifier,
      int nSightings,
      long closedAtMs) {}

  void putInstance(long instanceKey, OpenInstance instance);

  /** Returns the open instance or {@code null} when unknown (e.g. replay of a finished one). */
  OpenInstance getInstance(long instanceKey);

  void deleteInstance(long instanceKey);

  void putElement(long elementKey, OpenElement element);

  /** Returns the open element or {@code null} when unknown. */
  OpenElement getElement(long elementKey);

  void deleteElement(long elementKey);

  /** Stores a root-scope variable's current value (last write wins). */
  void putVariable(long instanceKey, String name, String valueJson);

  /** All root-scope variables of the instance, empty when none; iteration order unspecified. */
  Map<String, String> variablesOf(long instanceKey);

  /** Removes all variables of the instance (called on evict). */
  void deleteVariablesOf(long instanceKey);

  /** Stores (replaces) the open instance's variant-k1 accumulator. */
  void putVariantAccumulator(long instanceKey, VariantAccumulator accumulator);

  /** Returns the instance's variant-k1 accumulator, or {@code null} when unknown. */
  VariantAccumulator getVariantAccumulator(long instanceKey);

  /** Removes the instance's variant-k1 accumulator (called on instance eviction). */
  void deleteVariantAccumulator(long instanceKey);

  /**
   * Records that {@code bpmnProcessId}'s truncated id hash {@code h32} names {@code name} — a no-op
   * overwrite if already recorded (idempotent; see {@code LakeTranslator}'s heap-cache javadoc for
   * why this is only actually called on a cache miss).
   */
  void putVariantName(String bpmnProcessId, int h32, VariantName name);

  /**
   * Returns the name {@code bpmnProcessId}'s truncated id hash {@code h32} resolves to, or {@code
   * null}.
   */
  VariantName getVariantName(String bpmnProcessId, int h32);

  /**
   * Persists {@code flowId}'s resolved source/target element ids for {@code processDefinitionKey}.
   * Last-write-wins, like every other put on this store — parsing the same deployment record twice
   * (a replay) produces the identical result, so re-persisting it is idempotent, not merely safe.
   */
  void putFlowEndpoints(long processDefinitionKey, String flowId, FlowEndpoints endpoints);

  /**
   * Returns {@code flowId}'s resolved endpoints for {@code processDefinitionKey}, or {@code null}
   * when unknown — most commonly because that definition's own deployment record was never seen by
   * this translator (e.g. the source log's head was retention-trimmed before this translator's
   * bootstrap offset). Never guessed, never inferred — {@code null} is the honest answer.
   */
  FlowEndpoints flowEndpoints(long processDefinitionKey, String flowId);

  /** Stores (replaces) the open instance's accumulated object-sighting list. */
  void putObjectSightings(long instanceKey, ObjectSightingList sightings);

  /**
   * Returns the instance's accumulated object-sighting list, or {@code null} when none recorded.
   */
  ObjectSightingList getObjectSightings(long instanceKey);

  /** Removes the instance's object-sighting list (called on instance eviction). */
  void deleteObjectSightings(long instanceKey);

  /** Stores (replaces) {@code (objectType, objectId)}'s lifecycle accumulator. */
  void putObjectLifecycle(String objectType, String objectId, ObjectLifecycle lifecycle);

  /**
   * Returns {@code (objectType, objectId)}'s lifecycle accumulator, or {@code null} if this object
   * has never been sighted (the birth check — see {@code LakeTranslator}'s own "Object lifecycle
   * capture" javadoc section: {@code null} here is exactly the signal a fresh sighting is a birth).
   */
  ObjectLifecycle getObjectLifecycle(String objectType, String objectId);

  /**
   * Sweeps every {@link LifecycleStatus#CLOSED_TOMBSTONE} entry whose {@code closedAtMs} is
   * strictly less than {@code cutoffMs}, deleting it outright (unlike every other delete on this
   * store, this one is not preceded by an emit — a swept tombstone has already done its job of
   * blocking a re-birth/double-close for as long as {@code lake.objectTombstoneRetentionMs}
   * configures; see {@code LakePocApp}'s own housekeeping-tick wiring for when this runs and why a
   * bounded full-column-family scan is an acceptable cost there).
   *
   * @return the number of entries removed
   */
  int sweepObjectLifecycleTombstones(long cutoffMs);

  /**
   * Whether {@code processDefinitionKey}'s {@code process_definitions} dictionary row has already
   * been appended by this (or a prior) translator run — see {@link
   * LakeColumnFamilies#PROCESS_DEFINITIONS}'s own javadoc for why this marker exists (Zeebe
   * delivers the same deployment once per source partition) and {@code
   * io.camunda.analytics.lake.translate.LakeTranslator#onProcess} for how it is used.
   */
  boolean hasProcessDefinition(long processDefinitionKey);

  /**
   * Records that {@code processDefinitionKey}'s dictionary row has been appended — idempotent, like
   * every other put on this store. Never undone by a delete: see {@link
   * LakeColumnFamilies#PROCESS_DEFINITIONS}'s own javadoc for why.
   */
  void markProcessDefinition(long processDefinitionKey);

  /**
   * Full scan of the open instance set, keyed by process instance key. The caller is the single
   * writer thread, so iteration is consistent with the last applied record.
   */
  void forEachOpenInstance(BiConsumer<Long, OpenInstance> consumer);

  /**
   * Full scan of the open element set, keyed by element instance key. The caller is the single
   * writer thread, so iteration is consistent with the last applied record.
   */
  void forEachOpenElement(BiConsumer<Long, OpenElement> consumer);

  @Override
  void close();
}

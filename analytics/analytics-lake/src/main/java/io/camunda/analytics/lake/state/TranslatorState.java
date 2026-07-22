/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

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

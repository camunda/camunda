/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Immutable frozen segments and the merge machinery over them. Map of the four merge flavors and
 * who uses which:
 *
 * <ul>
 *   <li>{@link io.camunda.zeebe.db.layered.segment.FlatSegment#merge} — <em>materializing</em>
 *       compaction of a pipeline run into one segment (newest version per key, optional
 *       annihilation of never-flushed tombstones). Used by {@code
 *       LayeredKeyValueStore.mergePipelineIfNeeded} to bound read amplification.
 *   <li>{@link io.camunda.zeebe.db.layered.segment.FlushedOrMergeIterator} — <em>streaming</em>
 *       newest-version-per-key merge that additionally computes the sticky flushed-OR across
 *       shadowed versions. Used by {@code LayeredStoreCoordinator.PersistRound#drainStore} so a
 *       persist drain never materializes a merged segment and can skip never-flushed tombstones.
 *   <li>{@link io.camunda.zeebe.db.layered.segment.KWayMergeIterator} — plain k-way key-ordered
 *       merge of in-memory layers (staging/active selections plus pipeline segment ranges), upper
 *       layers shadowing lower ones. Used by the scan paths below as the "buffered" side.
 *   <li>{@link io.camunda.zeebe.db.layered.segment.ShadowingZipper} — zips a {@code
 *       KWayMergeIterator} over the durable stream (delegate or pinned snapshot), letting buffered
 *       entries shadow durable ones and tombstones hide them. Used by {@code
 *       LayeredKeyValueStore#prefixScan} (owner-thread scans) and {@code ReadOnlyView#prefixScan}
 *       (async reader scans).
 * </ul>
 */
package io.camunda.zeebe.db.layered.segment;

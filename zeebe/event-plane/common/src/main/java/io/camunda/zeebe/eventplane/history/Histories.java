/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A tenant's histories in a store. Each partition writes its own part of a history's manifest,
 * because a restore can start a history without anyone knowing all the partitions: a {@link
 * HistoryManifest} with only that partition, at {@link ObjectNames#historyPart}. Readers put the
 * parts of a history together.
 */
public final class Histories {

  private static final int PAGE_SIZE = 1000;

  private Histories() {}

  /**
   * Reads every part of every history of a tenant, and puts the parts of each history together: the
   * union of their partitions, and the latest {@code createdAt}.
   *
   * @throws InconsistentHistoryException if two parts of a history have different bases for a
   *     partition, or a part belongs to another cluster or tenant
   * @throws InvalidHistoryJsonException if a part isn't JSON of a manifest
   */
  public static HistoryLineage load(
      final ObjectStore store, final String cluster, final String tenant) throws IOException {
    return load(store, cluster, tenant, PAGE_SIZE);
  }

  static HistoryLineage load(
      final ObjectStore store, final String cluster, final String tenant, final int pageSize)
      throws IOException {
    final var prefix = ObjectNames.historiesPrefix(cluster, tenant);
    final var histories = new HashMap<UUID, HistoryManifest>();
    String startAfter = null;
    while (true) {
      final var page = store.list(prefix, startAfter, pageSize);
      for (final var object : page) {
        if (object.key().endsWith(".json")) {
          // A part that is deleted since the listing is no longer there.
          final var content = store.get(object.key());
          if (content.isPresent()) {
            final var part = ManifestJson.read(content.get());
            requireTenant(part, cluster, tenant, object.key());
            histories.merge(part.historyId(), part, Histories::merge);
          }
        }
      }
      if (page.size() < pageSize) {
        return new HistoryLineage(histories.values());
      }
      startAfter = page.getLast().key();
    }
  }

  /**
   * Writes a partition's part of a history's manifest: a manifest of only that partition. Every
   * write of it has the same bytes, so it can be repeated.
   *
   * @throws IllegalArgumentException if the manifest has more than one partition
   */
  public static void writePart(final ObjectStore store, final HistoryManifest manifest)
      throws IOException {
    if (manifest.partitions().size() != 1) {
      throw new IllegalArgumentException(
          "Expected a part of history %s to have one partition, but got %s"
              .formatted(manifest.historyId(), manifest.partitions().keySet()));
    }
    final int partition = manifest.partitions().keySet().iterator().next();
    final var log = new LogId(manifest.clusterId(), manifest.physicalTenantId(), partition);
    store.put(ObjectNames.historyPart(log, manifest.historyId()), ManifestJson.write(manifest));
  }

  private static void requireTenant(
      final HistoryManifest part, final String cluster, final String tenant, final String where) {
    if (!part.clusterId().equals(cluster) || !part.physicalTenantId().equals(tenant)) {
      throw new InconsistentHistoryException(
          part.historyId(),
          "Expected the part '%s' to belong to cluster '%s' and tenant '%s', but got '%s' and '%s'"
              .formatted(where, cluster, tenant, part.clusterId(), part.physicalTenantId()));
    }
  }

  /** Puts two parts of a history together. */
  private static HistoryManifest merge(final HistoryManifest a, final HistoryManifest b) {
    final var partitions = new TreeMap<>(a.partitions());
    for (final Map.Entry<Integer, HistoryManifest.Base> entry : b.partitions().entrySet()) {
      final var known = partitions.putIfAbsent(entry.getKey(), entry.getValue());
      if (known != null && !known.equals(entry.getValue())) {
        throw new InconsistentHistoryException(
            a.historyId(),
            "Expected the parts of history %s to agree on partition %d, but got %s and %s"
                .formatted(a.historyId(), entry.getKey(), known, entry.getValue()));
      }
    }
    final var createdAt = a.createdAt().isAfter(b.createdAt()) ? a.createdAt() : b.createdAt();
    return new HistoryManifest(
        a.version(), a.clusterId(), a.physicalTenantId(), a.historyId(), createdAt, partitions);
  }
}

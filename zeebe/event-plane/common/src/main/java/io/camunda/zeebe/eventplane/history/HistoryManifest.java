/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One history of a physical tenant, as in the Event Plane's design of history manifests: its id,
 * when a new tenant, a purge or a restore made it, and, for each of its partitions, whose journal
 * it takes over. {@link ManifestJson} reads and writes it as JSON.
 *
 * @param version the version of the manifest, 1
 * @param clusterId the cluster the history belongs to
 * @param physicalTenantId the physical tenant the history belongs to
 * @param historyId the history's id
 * @param createdAt when the history was made
 * @param partitions for each partition, whose journal the history takes over
 */
public record HistoryManifest(
    int version,
    String clusterId,
    String physicalTenantId,
    UUID historyId,
    Instant createdAt,
    Map<Integer, Base> partitions) {

  /** Orders a tenant's histories so that the current one comes last. */
  public static final Comparator<HistoryManifest> CURRENT_LAST =
      Comparator.comparing(HistoryManifest::createdAt)
          .thenComparing(manifest -> manifest.historyId().toString());

  public HistoryManifest {
    if (version != 1) {
      throw new IllegalArgumentException("Expected manifest version 1, but got " + version);
    }
    if (clusterId.isBlank() || physicalTenantId.isBlank()) {
      throw new IllegalArgumentException(
          "Expected a manifest to name its cluster and physical tenant, but got '%s' and '%s'"
              .formatted(clusterId, physicalTenantId));
    }
    Objects.requireNonNull(historyId, "historyId");
    Objects.requireNonNull(createdAt, "createdAt");
    if (partitions.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected a history of at least one partition, but got none");
    }
    partitions.forEach(
        (partition, base) -> {
          if (partition < 1) {
            throw new IllegalArgumentException(
                "Expected a partition of 1 or more, but got " + partition);
          }
          if (historyId.equals(base.sourceHistoryId())) {
            throw new IllegalArgumentException(
                "Expected history %s to take over another history in partition %d, but got itself"
                    .formatted(historyId, partition));
          }
        });
    partitions = Collections.unmodifiableMap(new TreeMap<>(partitions));
  }

  /**
   * Whose journal a partition takes over: the source history's, up to and including the entry at
   * {@code index}, which holds the record at {@code position}. A new tenant and a purge take over
   * nothing: no source, index 0 and position -1.
   *
   * @param sourceHistoryId the history whose journal is taken over, or null for none
   * @param index the Raft index of the last entry taken over
   * @param position the position of the record in that entry
   */
  public record Base(@Nullable UUID sourceHistoryId, long index, long position) {

    public static final Base NOTHING = new Base(null, 0, -1);

    public Base {
      if (sourceHistoryId == null ? index != 0 || position != -1 : index < 1 || position < 1) {
        throw new IllegalArgumentException(
            "Expected a source's checkpoint at index and position 1 or later, or no source at index 0 and position -1, but got source %s, index %d and position %d"
                .formatted(sourceHistoryId, index, position));
      }
    }
  }
}

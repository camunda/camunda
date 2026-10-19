/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.history;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Immutable tenant history metadata. Publication, rather than creation, activates a history. */
@NullMarked
public record HistoryManifest(
    int version,
    String clusterId,
    String physicalTenantId,
    UUID historyId,
    Instant createdAt,
    Map<Integer, Base> partitions) {

  public static final Comparator<HistoryManifest> ORDER =
      Comparator.comparing(HistoryManifest::createdAt)
          .thenComparing(manifest -> manifest.historyId().toString());

  public HistoryManifest {
    if (version != 1) {
      throw new IllegalArgumentException("Unsupported history manifest version: " + version);
    }
    if (Objects.requireNonNull(clusterId).isBlank()
        || Objects.requireNonNull(physicalTenantId).isBlank()) {
      throw new IllegalArgumentException("Cluster and physical tenant IDs must not be blank");
    }
    Objects.requireNonNull(historyId);
    Objects.requireNonNull(createdAt);
    Objects.requireNonNull(partitions);
    if (partitions.isEmpty()) {
      throw new IllegalArgumentException("A history must describe its partitions");
    }
    partitions.forEach(
        (partition, base) -> {
          if (partition <= 0) {
            throw new IllegalArgumentException("Partition IDs must be positive");
          }
          Objects.requireNonNull(base);
          if (historyId.equals(base.sourceHistoryId())) {
            throw new IllegalArgumentException("A history cannot inherit from itself");
          }
        });
    partitions = Collections.unmodifiableMap(new TreeMap<>(partitions));
  }

  /** The inherited prefix, inclusive; null with two zeroes denotes an empty root. */
  public record Base(@Nullable UUID sourceHistoryId, long index, long position) {
    public Base {
      if (sourceHistoryId == null ? index != 0 || position != 0 : index <= 0 || position <= 0) {
        throw new IllegalArgumentException("A base must be an empty root or a source checkpoint");
      }
    }

    public static Base root() {
      return new Base(null, 0, 0);
    }
  }
}

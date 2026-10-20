/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * How histories relate, from their manifests, as in the Event Plane's design of history manifests.
 * In a partition, a history's path is the stretches of journal that its ancestors appended, oldest
 * first, each up to the checkpoint where the next one took over, and then its own entries.
 *
 * <p>A history's segments hold its journal, including the entries it took over. A segment of
 * history {@code H} serves the current history's path for every index up to {@code lastShared(H,
 * current).index}, or for every index if {@code H} is the current history.
 */
public final class HistoryLineage {

  private final Map<UUID, HistoryManifest> manifests = new HashMap<>();

  /** Takes the same manifest more than once, as every store with a history's segments has it. */
  public HistoryLineage(final Collection<HistoryManifest> manifests) {
    for (final var manifest : manifests) {
      final var known = this.manifests.putIfAbsent(manifest.historyId(), manifest);
      if (known != null && !known.equals(manifest)) {
        throw new InconsistentHistoryException(
            manifest.historyId(),
            "Expected one manifest of history %s, but got two that differ: %s and %s"
                .formatted(manifest.historyId(), known, manifest));
      }
    }
  }

  /** The tenant's current history: the one made last. */
  public Optional<HistoryManifest> current(final String clusterId, final String physicalTenantId) {
    return manifests.values().stream()
        .filter(m -> m.clusterId().equals(clusterId))
        .filter(m -> m.physicalTenantId().equals(physicalTenantId))
        .max(HistoryManifest.CURRENT_LAST);
  }

  /** The manifest of a history, if the lineage has it. */
  public Optional<HistoryManifest> manifest(final UUID historyId) {
    return Optional.ofNullable(manifests.get(historyId));
  }

  /** All the manifests, in the order of {@link HistoryManifest#CURRENT_LAST}. */
  public List<HistoryManifest> manifests() {
    return manifests.values().stream().sorted(HistoryManifest.CURRENT_LAST).toList();
  }

  /**
   * The last entry two histories of a tenant share in a partition, or {@link Base#NOTHING} if they
   * share none, as when one of them leaves the partition out.
   *
   * @throws MissingManifestException while a manifest it needs is missing, which may still arrive
   * @throws InconsistentHistoryException for manifests that contradict each other
   */
  public Base lastShared(final UUID a, final UUID b, final int partition) {
    if (a.equals(b)) {
      throw new IllegalArgumentException(
          "Expected two different histories, but got %s twice".formatted(a));
    }
    if (!sameTenant(require(a), require(b))) {
      throw new InconsistentHistoryException(
          a,
          "Expected histories %s and %s to belong to one physical tenant, but got two"
              .formatted(a, b));
    }
    final var left = path(a, partition);
    final var right = path(b, partition);
    var shared = Base.NOTHING;
    for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
      final var x = left.get(i);
      final var y = right.get(i);
      if (!x.history().equals(y.history())) {
        break;
      }
      // Both take this history over from the same checkpoint, and share it up to where the first
      // of them leaves it.
      shared = earlier(x.end(), y.end());
      if (!Objects.equals(x.end(), y.end())) {
        break;
      }
    }
    return shared;
  }

  /**
   * A history's path through a partition, oldest first; empty if it leaves the partition out. It
   * walks back from the history to the one that took over nothing, each stretch ending where the
   * history after it took over. Every checkpoint lies after its source's own, so the walk ends.
   *
   * @throws MissingManifestException while a manifest it needs is missing, which may still arrive
   * @throws InconsistentHistoryException for manifests that contradict each other
   */
  public List<Stretch> path(final UUID historyId, final int partition) {
    final var own = require(historyId);
    var base = own.partitions().get(partition);
    if (base == null) {
      return List.of();
    }
    final var path = new ArrayList<Stretch>();
    path.add(new Stretch(historyId, base, null));
    while (true) {
      final var sourceId = base.sourceHistoryId();
      if (sourceId == null) {
        return path.reversed();
      }
      final var source = require(sourceId);
      if (!sameTenant(source, own)) {
        throw new InconsistentHistoryException(
            sourceId,
            "Expected history %s to belong to the physical tenant of %s that takes it over, but got another"
                .formatted(sourceId, historyId));
      }
      final var sourceBase = source.partitions().get(partition);
      if (sourceBase == null) {
        throw new InconsistentHistoryException(
            sourceId,
            "Expected history %s to have partition %d, which %s takes over, but got none"
                .formatted(sourceId, partition, path.getLast().history()));
      }
      if (base.index() <= sourceBase.index() || base.position() <= sourceBase.position()) {
        throw new InconsistentHistoryException(
            sourceId,
            "Expected a history to take over %s after that history's own entries, which start after index %d and position %d, but got index %d and position %d"
                .formatted(
                    sourceId,
                    sourceBase.index(),
                    sourceBase.position(),
                    base.index(),
                    base.position()));
      }
      path.add(new Stretch(sourceId, sourceBase, base));
      base = sourceBase;
    }
  }

  /** The earlier of two checkpoints in one history; null stands for the history's own open end. */
  private static Base earlier(@Nullable final Base x, @Nullable final Base y) {
    if (x == null || y == null) {
      final var closed = x == null ? y : x;
      if (closed == null) {
        throw new IllegalStateException(
            "Expected two histories to differ in where they end, but both are open");
      }
      return closed;
    }
    if (x.index() == y.index() && x.position() != y.position()) {
      throw new InconsistentHistoryException(
          Objects.requireNonNull(x.sourceHistoryId()),
          "Expected two checkpoints at index %d of history %s to hold one position, but got %d and %d"
              .formatted(x.index(), x.sourceHistoryId(), x.position(), y.position()));
    }
    return x.index() <= y.index() ? x : y;
  }

  private HistoryManifest require(final UUID historyId) {
    final var manifest = manifests.get(historyId);
    if (manifest == null) {
      throw new MissingManifestException(historyId);
    }
    return manifest;
  }

  private static boolean sameTenant(final HistoryManifest a, final HistoryManifest b) {
    return a.clusterId().equals(b.clusterId()) && a.physicalTenantId().equals(b.physicalTenantId());
  }

  /**
   * The entries one history appended in a path: after {@code start}, its checkpoint in its source,
   * up to and including {@code end}, where the next history took over, or on if that is null.
   *
   * @param history the history that appended the entries
   * @param start the history's checkpoint in its source, or {@link Base#NOTHING}
   * @param end the checkpoint in this history where the next one took over, or null for the history
   *     the path is of, which goes on
   */
  public record Stretch(UUID history, Base start, @Nullable Base end) {}
}

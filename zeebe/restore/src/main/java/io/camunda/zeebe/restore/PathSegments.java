/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.eventplane.history.HistoryLineage.Stretch;
import io.camunda.zeebe.eventplane.store.InvalidObjectNameException;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.SegmentName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The segments that serve a path through a partition: those of each history on the path, each up to
 * the entry where the next history took over, or on for the path's own history. A history's
 * segments hold the entries it took over too, which are the same bytes.
 */
final class PathSegments {

  private static final int PAGE_SIZE = 1000;
  private static final String SEGMENT_SUFFIX = ".seg";

  private final ObjectStore store;
  private final String cluster;
  private final String tenant;
  private final int partition;
  private final List<Stretch> path;
  private List<PathSegment> segments = List.of();

  PathSegments(
      final ObjectStore store,
      final String cluster,
      final String tenant,
      final int partition,
      final List<Stretch> path) {
    this.store = store;
    this.cluster = cluster;
    this.tenant = tenant;
    this.partition = partition;
    this.path = List.copyOf(path);
  }

  /** Lists the segments of the path's histories again. */
  void refresh() throws IOException {
    final var listed = new ArrayList<PathSegment>();
    for (final var stretch : path) {
      final var end = stretch.end();
      final long servesUpTo = end == null ? Long.MAX_VALUE : end.index();
      final var prefix = ObjectNames.segmentsPrefix(cluster, tenant, partition, stretch.history());
      String startAfter = null;
      while (true) {
        final var page = store.list(prefix, startAfter, PAGE_SIZE);
        for (final var object : page) {
          final var name = parse(object.key());
          if (name != null && name.firstIndex() <= servesUpTo) {
            listed.add(new PathSegment(object.key(), name, servesUpTo));
          }
        }
        if (page.size() < PAGE_SIZE) {
          break;
        }
        startAfter = page.getLast().key();
      }
    }
    segments = List.copyOf(listed);
  }

  /**
   * The segment to read the entry at {@code index} from: among those that serve it, the one with
   * the highest level, then the one that serves the most entries after it.
   */
  Optional<PathSegment> holding(final long index) {
    return segments.stream()
        .filter(segment -> segment.serves(index))
        .max(
            Comparator.comparingInt((PathSegment segment) -> segment.name().level())
                .thenComparingLong(PathSegment::lastServed));
  }

  private static @Nullable SegmentName parse(final String key) {
    if (!key.endsWith(SEGMENT_SUFFIX)) {
      return null;
    }
    try {
      return ObjectNames.parseSegment(key);
    } catch (final InvalidObjectNameException e) {
      return null;
    }
  }

  /**
   * A segment of a history on the path.
   *
   * @param key its key
   * @param name what its key says
   * @param servesUpTo the last index for which its history serves the path
   */
  record PathSegment(String key, SegmentName name, long servesUpTo) {

    /** The last entry of the segment that serves the path. */
    long lastServed() {
      return Math.min(name.lastIndex(), servesUpTo);
    }

    boolean serves(final long index) {
      return name.firstIndex() <= index && index <= lastServed();
    }
  }
}

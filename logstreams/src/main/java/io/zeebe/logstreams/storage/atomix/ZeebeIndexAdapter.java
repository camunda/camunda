/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.index.JournalIndex;
import io.atomix.storage.journal.index.Position;
import io.atomix.storage.journal.index.SparseJournalIndex;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public final class ZeebeIndexAdapter implements JournalIndex, ZeebeIndexMapping {

  private final ConcurrentNavigableMap<Long, Long> positionIndexMapping =
      new ConcurrentSkipListMap<>();
  private final ConcurrentNavigableMap<Long, Long> indexPositionMapping =
      new ConcurrentSkipListMap<>();
  private final SparseJournalIndex sparseJournalIndex;
  private final int density;

  private ZeebeIndexAdapter(final int density) {
    this.density = density;
    sparseJournalIndex = new SparseJournalIndex(density);
  }

  public static ZeebeIndexAdapter ofDensity(final int density) {
    return new ZeebeIndexAdapter(density);
  }

  @Override
  public void index(final Indexed indexedEntry, final int position) {
    final var index = indexedEntry.index();
    if (index % density == 0) {
      if (indexedEntry.type() == ZeebeEntry.class) {
        final ZeebeEntry zeebeEntry = (ZeebeEntry) indexedEntry.entry();
        final var lowestPosition = zeebeEntry.lowestPosition();

        positionIndexMapping.put(lowestPosition, index);
        indexPositionMapping.put(index, lowestPosition);
      }
    }

    sparseJournalIndex.index(indexedEntry, position);
  }

  @Override
  public Position lookup(final long index) {
    return sparseJournalIndex.lookup(index);
  }

  @Override
  public void truncate(final long index) {
    final var higherEntry = indexPositionMapping.higherEntry(index);

    if (higherEntry != null) {

      final var higherIndex = higherEntry.getKey();
      final var higherPosition = higherEntry.getValue();

      indexPositionMapping.tailMap(higherIndex).clear();
      positionIndexMapping.tailMap(higherPosition).clear();
    }

    sparseJournalIndex.truncate(index);
  }

  @Override
  public void compact(final long index) {
    final var lowerEntry = indexPositionMapping.lowerEntry(index);

    if (lowerEntry != null) {

      final var lowerIndex = lowerEntry.getKey();
      final var lowerPosition = lowerEntry.getValue();

      indexPositionMapping.headMap(lowerIndex).clear();
      positionIndexMapping.headMap(lowerPosition).clear();
    }

    sparseJournalIndex.compact(index);
  }

  @Override
  public long lookupPosition(final long position) {
    long index = -1L;

    final var lowerEntry = positionIndexMapping.floorEntry(position);
    if (lowerEntry != null) {
      index = lowerEntry.getValue();
    }

    return index;
  }
}

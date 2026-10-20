/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.qa;

import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.segment.EntryReader;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

/** What a test sees in the bucket, read with the segment format alone. */
final class Bucket {

  private Bucket() {}

  /** The logs in the bucket. */
  static List<LogRef> logs(final ObjectStore store) throws IOException {
    final var logs = new ArrayList<LogRef>();
    for (final var cluster : store.listPrefixes("")) {
      if (cluster.equals(ObjectNames.groupsPrefix())) {
        continue;
      }
      for (final var tenant : store.listPrefixes(cluster)) {
        for (final var partition : store.listPrefixes(tenant)) {
          final var name = last(partition);
          if (name.chars().allMatch(Character::isDigit)) {
            logs.add(
                new LogRef(
                    ObjectNames.decode(last(cluster)),
                    ObjectNames.decode(last(tenant)),
                    Integer.parseInt(name)));
          }
        }
      }
    }
    return logs;
  }

  /** Every entry of every segment of a log, in all its histories. */
  static LogRead readLog(final ObjectStore store, final LogRef log) throws IOException {
    final var entries = new ArrayList<Entry>();
    final var histories = new ArrayList<UUID>();
    final var prefix = ObjectNames.logPrefix(log.clusterId(), log.tenantId(), log.partition());
    for (final var directory : store.listPrefixes(prefix)) {
      final UUID history;
      try {
        history = UUID.fromString(last(directory));
      } catch (final IllegalArgumentException notAHistory) {
        continue;
      }
      histories.add(history);
      for (final var object : store.list(directory, null, Integer.MAX_VALUE)) {
        final var content = store.get(object.key()).orElseThrow();
        final var footer = Footer.read(ByteBuffer.wrap(content));
        try (final var reader =
            new EntryReader(new ByteArrayInputStream(content, 0, (int) footer.bodyLength()))) {
          for (var entry = reader.next(); entry != null; entry = reader.next()) {
            entries.add(entry);
          }
        }
      }
    }
    return new LogRead(histories, entries);
  }

  private static String last(final String prefix) {
    final var trimmed = prefix.substring(0, prefix.length() - 1);
    return trimmed.substring(trimmed.lastIndexOf('/') + 1);
  }

  record LogRef(String clusterId, String tenantId, int partition) {}

  /** The entries read, in the order of their segments' names. */
  record LogRead(List<UUID> histories, List<Entry> all) {

    UUID history() {
      return histories.getLast();
    }

    long entries() {
      return distinct().size();
    }

    long lastPosition() {
      return all.stream().filter(Entry::hasRecords).mapToLong(Entry::lastPosition).max().orElse(-1);
    }

    /** Entries uploaded more than once, as after a leader change or a restart. */
    long duplicateEntries() {
      return all.size() - distinct().size();
    }

    /** Every index from the first to the last, each with the same bytes wherever it repeats. */
    boolean indexesAreContiguous() {
      final var byIndex = distinct();
      final boolean sameBytes =
          all.stream().allMatch(entry -> byIndex.get(entry.index()).equals(entry));
      return sameBytes
          && (byIndex.isEmpty() || byIndex.lastKey() - byIndex.firstKey() + 1 == byIndex.size());
    }

    boolean positionsIncrease() {
      long previous = 0;
      for (final var entry : distinct().values()) {
        if (entry.hasRecords()) {
          if (entry.firstPosition() <= previous) {
            return false;
          }
          previous = entry.lastPosition();
        }
      }
      return true;
    }

    /** The entries by index, the first of each. */
    private TreeMap<Long, Entry> distinct() {
      final var byIndex = new TreeMap<Long, Entry>();
      all.forEach(entry -> byIndex.putIfAbsent(entry.index(), entry));
      return byIndex;
    }
  }
}

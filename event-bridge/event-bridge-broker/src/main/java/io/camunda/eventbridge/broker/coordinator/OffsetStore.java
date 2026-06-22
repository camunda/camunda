/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Durable per-{@code (groupId, partitionId)} committed offsets for the coordinator.
 *
 * <p>POC durability: the full offset table is written to a single file in the coordinator
 * partition's data directory on every commit (the data is tiny) and reloaded when the coordinator
 * becomes leader, so committed offsets survive a coordinator restart. With replication factor 1
 * this survives node restart; true failover to another broker needs replicated commits (a follow-up
 * step). Not thread-safe — accessed only from the coordinator actor thread.
 *
 * <p>File format: one {@code groupId\tpartitionId\toffset} line per entry.
 */
public final class OffsetStore {

  private static final Logger LOG = LoggerFactory.getLogger(OffsetStore.class);
  private static final String FILE_NAME = "consumer-offsets";

  private final Path file;
  private final Path tmpFile;
  private final Map<String, Map<Integer, Long>> offsetsByGroup = new HashMap<>();

  public OffsetStore(final Path directory) {
    file = directory.resolve(FILE_NAME);
    tmpFile = directory.resolve(FILE_NAME + ".tmp");
  }

  /** Loads persisted offsets from disk, replacing the in-memory state. */
  public void load() {
    offsetsByGroup.clear();
    if (!Files.exists(file)) {
      return;
    }
    try {
      for (final var line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        if (line.isBlank()) {
          continue;
        }
        final var parts = line.split("\t");
        if (parts.length != 3) {
          continue;
        }
        offsetsByGroup
            .computeIfAbsent(parts[0], g -> new HashMap<>())
            .put(Integer.parseInt(parts[1]), Long.parseLong(parts[2]));
      }
      LOG.info("Loaded committed offsets for {} group(s) from {}", offsetsByGroup.size(), file);
    } catch (final IOException | RuntimeException e) {
      LOG.warn("Failed to load committed offsets from {}; starting empty", file, e);
      offsetsByGroup.clear();
    }
  }

  /**
   * Commits a position for a (group, partition), monotonically (never moves backwards), and
   * persists. Returns the resulting committed position.
   */
  public long commit(final String groupId, final int partitionId, final long position) {
    final var partitions = offsetsByGroup.computeIfAbsent(groupId, g -> new HashMap<>());
    final long committed = Math.max(partitions.getOrDefault(partitionId, -1L), position);
    partitions.put(partitionId, committed);
    persist();
    return committed;
  }

  /** Returns the committed offsets for a group (partition -> next position), possibly empty. */
  public Map<Integer, Long> getOffsets(final String groupId) {
    return new TreeMap<>(offsetsByGroup.getOrDefault(groupId, Map.of()));
  }

  private void persist() {
    final List<String> lines = new ArrayList<>();
    offsetsByGroup.forEach(
        (groupId, partitions) ->
            partitions.forEach(
                (partitionId, offset) -> lines.add(groupId + "\t" + partitionId + "\t" + offset)));
    try {
      Files.write(tmpFile, lines, StandardCharsets.UTF_8);
      Files.move(
          tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to persist committed offsets to " + file, e);
    }
  }
}

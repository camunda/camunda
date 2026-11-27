/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.broker.Loggers;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The last {@code capacity} entries of a broker's health history, kept in memory and, if a file is
 * given, appended to that file as one JSON document per line. The file survives restarts and
 * crashes, which is what lets the broker tell after a start whether its previous run stopped
 * cleanly. Each write is flushed to the operating system but not synced to disk: a crash of the
 * process keeps it, a crash of the machine can lose the last entries.
 *
 * <p>The file is rewritten with the last {@code capacity} entries once it holds twice as many.
 * Thread safe.
 */
public final class HealthHistory implements HealthEvents, AutoCloseable {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final String root;
  private final int capacity;
  private final @Nullable Path file;
  private final InstantSource clock;
  private final Deque<HealthHistoryEntry> entries = new ArrayDeque<>();
  private @Nullable BufferedWriter writer;
  private long nextSequence;
  private int linesInFile;

  /**
   * @param root the component name of the broker's health tree root, for example {@code Broker-0};
   *     relative components of {@link #record} are put under it
   * @param file where to keep the history across restarts, or null to keep it in memory only
   */
  public HealthHistory(
      final String root, final int capacity, final @Nullable Path file, final InstantSource clock) {
    if (capacity < 1) {
      throw new IllegalArgumentException("Expected a capacity of at least 1, but got " + capacity);
    }
    this.root = root;
    this.capacity = capacity;
    this.file = file;
    this.clock = clock;
    if (file != null) {
      load(file);
    }
  }

  @Override
  public void record(
      final String component,
      final String type,
      final @Nullable String message,
      final Map<String, String> details) {
    recordAt(component.isEmpty() ? root : root + "/" + component, type, message, details);
  }

  /** Records an entry for a component given by its full path, starting with the root. */
  public synchronized void recordAt(
      final String path,
      final String type,
      final @Nullable String message,
      final Map<String, String> details) {
    final var entry =
        new HealthHistoryEntry(nextSequence++, clock.instant(), path, type, message, details);
    entries.addLast(entry);
    while (entries.size() > capacity) {
      entries.removeFirst();
    }
    append(entry);
  }

  /** The broker's health tree root, for example {@code Broker-0}. */
  public String root() {
    return root;
  }

  /** Every entry held, oldest first, including those loaded from a previous run. */
  public synchronized List<HealthHistoryEntry> entries() {
    return List.copyOf(entries);
  }

  /**
   * The newest {@code limit} entries that match every given filter, oldest first.
   *
   * @param since only entries recorded at or after this time, or null for all
   * @param componentPrefix only entries of this component or one below it, or null for all
   * @param type only entries of this type, or null for all
   */
  public synchronized List<HealthHistoryEntry> query(
      final @Nullable Instant since,
      final @Nullable String componentPrefix,
      final @Nullable String type,
      final int limit) {
    final var matching = new ArrayList<HealthHistoryEntry>();
    final var newestFirst = entries.descendingIterator();
    while (newestFirst.hasNext() && matching.size() < limit) {
      final var entry = newestFirst.next();
      if (since != null && entry.timestamp().isBefore(since)) {
        break;
      }
      if (matches(entry, componentPrefix, type)) {
        matching.add(entry);
      }
    }
    Collections.reverse(matching);
    return matching;
  }

  @Override
  public synchronized void close() {
    if (writer == null) {
      return;
    }
    try {
      writer.close();
    } catch (final IOException e) {
      LOG.warn("Failed to close the health history file {}", file, e);
    }
    writer = null;
  }

  private static boolean matches(
      final HealthHistoryEntry entry,
      final @Nullable String componentPrefix,
      final @Nullable String type) {
    if (type != null && !type.equals(entry.type())) {
      return false;
    }
    return componentPrefix == null
        || entry.component().equals(componentPrefix)
        || entry.component().startsWith(componentPrefix + "/");
  }

  private void load(final Path file) {
    if (Files.exists(file)) {
      try (final var lines = Files.lines(file, StandardCharsets.UTF_8)) {
        lines.forEach(this::loadLine);
      } catch (final IOException | RuntimeException e) {
        LOG.warn("Failed to read the health history file {}; continuing without it", file, e);
      }
    }
    nextSequence = entries.isEmpty() ? 0 : entries.getLast().sequence() + 1;
    rewrite();
  }

  private void loadLine(final String line) {
    if (line.isBlank()) {
      return;
    }
    try {
      entries.addLast(MAPPER.readValue(line, HealthHistoryEntry.class));
      while (entries.size() > capacity) {
        entries.removeFirst();
      }
    } catch (final IOException e) {
      // a crash can leave a partly written last line behind
      LOG.debug("Skipping an unreadable line of the health history file", e);
    }
  }

  private void append(final HealthHistoryEntry entry) {
    if (writer == null) {
      return;
    }
    try {
      writer.write(MAPPER.writeValueAsString(entry));
      writer.newLine();
      writer.flush();
      linesInFile++;
    } catch (final IOException e) {
      LOG.warn("Failed to append to the health history file {}; keeping it in memory", file, e);
      close();
      return;
    }
    if (linesInFile >= 2 * capacity) {
      rewrite();
    }
  }

  /** Replaces the file with the entries held in memory, and appends to it from then on. */
  private void rewrite() {
    if (file == null) {
      return;
    }
    close();
    final var temporary = file.resolveSibling(file.getFileName() + ".tmp");
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      try (final var out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        for (final var entry : entries) {
          out.write(MAPPER.writeValueAsString(entry));
          out.newLine();
        }
      }
      Files.move(
          temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      writer =
          Files.newBufferedWriter(
              file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      linesInFile = entries.size();
    } catch (final IOException e) {
      LOG.warn("Failed to write the health history file {}; keeping it in memory", file, e);
    }
  }
}

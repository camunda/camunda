/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The history that a restore of a partition starts, as it leaves it in the partition's data
 * directory in {@value #FILE_NAME} for the leader's publisher to find on its first start:
 *
 * <pre>{@code
 * {"history":"…","createdAt":"2026-10-20T12:00:00Z","source":"…","index":1096704,"position":1052032}
 * }</pre>
 *
 * @param history the new history
 * @param createdAt when the restore made it
 * @param source the history whose journal the restore took over, or null if the restore doesn't
 *     know it: a restore from a backup leaves it to the publisher, which takes the history its
 *     restored upload floor names
 * @param index the Raft index of the checkpoint's entry, the last restored one
 * @param position the position of the checkpoint record in that entry
 */
public record RestoredHistory(
    UUID history, Instant createdAt, @Nullable UUID source, long index, long position) {

  /** The name of the file in a partition's data directory. */
  public static final String FILE_NAME = "event-plane-history.json";

  public RestoredHistory {
    if (history.equals(source)) {
      throw new IllegalArgumentException(
          "Expected history %s to take over another history, but got itself".formatted(history));
    }
    if (index < 1 || position < 1) {
      throw new IllegalArgumentException(
          "Expected a restored checkpoint at index and position 1 or later, but got index %d and position %d"
              .formatted(index, position));
    }
  }

  /** What the history takes over from {@code source}, as it goes into its manifest. */
  public Base baseIn(final UUID source) {
    return new Base(source, index, position);
  }

  /** The file's content, always the same bytes for the same history. */
  public byte[] toJson() {
    final var root = JsonObject.MAPPER.createObjectNode();
    root.put("history", history.toString());
    root.put("createdAt", createdAt.toString());
    if (source == null) {
      root.putNull("source");
    } else {
      root.put("source", source.toString());
    }
    root.put("index", index);
    root.put("position", position);
    try {
      return JsonObject.MAPPER.writeValueAsBytes(root);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads the file's content. Fields that it doesn't know are skipped.
   *
   * @throws InvalidHistoryJsonException if the JSON isn't that of a restored history
   * @throws IOException if the bytes aren't JSON
   */
  public static RestoredHistory fromJson(final byte[] json) throws IOException {
    final var root = JsonObject.parse(json);
    try {
      return new RestoredHistory(
          root.uuid("history"),
          root.instant("createdAt"),
          root.optionalUuid("source"),
          root.longValue("index"),
          root.longValue("position"));
    } catch (final IllegalArgumentException e) {
      throw new InvalidHistoryJsonException(
          "the document", "a valid restored history", JsonObject.shown(json), e);
    }
  }

  /**
   * Leaves the history in a partition's data directory, for the publisher to find. The file appears
   * whole or not at all.
   */
  public void writeTo(final Path partitionDirectory) throws IOException {
    final var temporary = partitionDirectory.resolve(FILE_NAME + ".tmp");
    Files.write(temporary, toJson());
    Files.move(
        temporary,
        partitionDirectory.resolve(FILE_NAME),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
  }

  /** The history that a restore left in a partition's data directory, if it left one. */
  public static Optional<RestoredHistory> readFrom(final Path partitionDirectory)
      throws IOException {
    try {
      return Optional.of(fromJson(Files.readAllBytes(partitionDirectory.resolve(FILE_NAME))));
    } catch (final NoSuchFileException e) {
      return Optional.empty();
    }
  }
}

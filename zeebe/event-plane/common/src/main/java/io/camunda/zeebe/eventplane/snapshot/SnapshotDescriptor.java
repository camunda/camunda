/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.eventplane.history.HistoryLineage.Stretch;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a snapshot of a partition in the store is, written after its files as {@code
 * descriptor.json}:
 *
 * <pre>{@code
 * {"version":1,"snapshotId":"…","index":1096000,"term":3,"processedPosition":1051900,
 *  "exportedPosition":1051800,"maxExportedPosition":1052032,"lastFollowupEventPosition":1052010,
 *  "history":"…","files":[{"name":"000123.sst","size":4096}],"createdAt":"2026-10-20T12:00:00Z"}
 * }</pre>
 *
 * @param version the descriptor's version, 1
 * @param snapshotId the snapshot store's id of the snapshot, which names its directory
 * @param index the snapshot's Raft index: the journal must hold every entry after it
 * @param term the term of the entry at that index
 * @param processedPosition the last position the state processed
 * @param exportedPosition the lowest exporter position in the state, the upload floor's included
 * @param maxExportedPosition the highest exporter position in the state, the upload floor's
 *     included
 * @param lastFollowupEventPosition the last position written when the snapshot was taken: the log
 *     must reach it for the state to be consistent with the log
 * @param history the history its leader published in when it uploaded the snapshot
 * @param files the snapshot's files, its checksum file among them, by name
 * @param createdAt when the snapshot was uploaded
 */
public record SnapshotDescriptor(
    int version,
    String snapshotId,
    long index,
    long term,
    long processedPosition,
    long exportedPosition,
    long maxExportedPosition,
    long lastFollowupEventPosition,
    UUID history,
    List<SnapshotFile> files,
    Instant createdAt) {

  /** The version this class writes and reads. */
  public static final int VERSION = 1;

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern UUID_TEXT =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final int MAX_SHOWN = 200;

  public SnapshotDescriptor {
    if (version != VERSION) {
      throw new IllegalArgumentException(
          "Expected snapshot descriptor version %d, but got %d".formatted(VERSION, version));
    }
    if (snapshotId.isEmpty()) {
      throw new IllegalArgumentException("Expected a snapshot id, but got an empty one");
    }
    if (index < 0 || term < 0) {
      throw new IllegalArgumentException(
          "Expected a snapshot index and term of 0 or more, but got %d and %d"
              .formatted(index, term));
    }
    if (files.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected snapshot %s to have files, but got none".formatted(snapshotId));
    }
    final var names = new HashSet<String>();
    for (final var file : files) {
      if (!names.add(file.name())) {
        throw new IllegalArgumentException(
            "Expected the files of snapshot %s to have distinct names, but got '%s' twice"
                .formatted(snapshotId, file.name()));
      }
    }
    files = List.copyOf(files);
  }

  /**
   * The highest position the snapshot relies on: a journal restored from it has to reach it, or the
   * state would hold what the journal doesn't.
   */
  public long requiredPosition() {
    return Math.max(
        Math.max(processedPosition, exportedPosition),
        Math.max(maxExportedPosition, lastFollowupEventPosition));
  }

  /**
   * Whether a restore along this path can start from the snapshot: its history is on the path, and,
   * unless that is the path's own history, every position the snapshot relies on lies at or before
   * the checkpoint where the next history took over.
   *
   * @param path a history's path through the snapshot's partition, oldest first, as {@link
   *     io.camunda.zeebe.eventplane.history.HistoryLineage#path} gives it
   */
  public boolean servesPath(final List<Stretch> path) {
    for (final var stretch : path) {
      if (stretch.history().equals(history)) {
        final var end = stretch.end();
        return end == null || requiredPosition() <= end.position();
      }
    }
    return false;
  }

  /** The descriptor as JSON, in the contract's field order. */
  public byte[] toJson() {
    final var root = MAPPER.createObjectNode();
    root.put("version", version);
    root.put("snapshotId", snapshotId);
    root.put("index", index);
    root.put("term", term);
    root.put("processedPosition", processedPosition);
    root.put("exportedPosition", exportedPosition);
    root.put("maxExportedPosition", maxExportedPosition);
    root.put("lastFollowupEventPosition", lastFollowupEventPosition);
    root.put("history", history.toString());
    final var array = root.putArray("files");
    for (final var file : files) {
      array.addObject().put("name", file.name()).put("size", file.size());
    }
    root.put("createdAt", createdAt.toString());
    try {
      return MAPPER.writeValueAsBytes(root);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads a descriptor. Fields that it doesn't know are skipped.
   *
   * @throws InvalidSnapshotDescriptorException if the JSON isn't that of a valid descriptor
   * @throws IOException if the bytes aren't JSON
   */
  public static SnapshotDescriptor fromJson(final byte[] json) throws IOException {
    final var root = MAPPER.readTree(json);
    if (root == null || !root.isObject()) {
      throw new InvalidSnapshotDescriptorException("the document", "a JSON object", shown(json));
    }
    final var filesNode = root.get("files");
    if (filesNode == null || !filesNode.isArray()) {
      throw invalid("files", "an array", filesNode);
    }
    try {
      final var files = new ArrayList<SnapshotFile>();
      for (int i = 0; i < filesNode.size(); i++) {
        final var file = filesNode.get(i);
        final var path = "files." + i;
        if (!file.isObject()) {
          throw invalid(path, "a JSON object", file);
        }
        files.add(new SnapshotFile(text(file, path, "name"), number(file, path, "size")));
      }
      final long version = number(root, "", "version");
      if (version != VERSION) {
        throw invalid("version", String.valueOf(VERSION), root.get("version"));
      }
      return new SnapshotDescriptor(
          VERSION,
          text(root, "", "snapshotId"),
          number(root, "", "index"),
          number(root, "", "term"),
          number(root, "", "processedPosition"),
          number(root, "", "exportedPosition"),
          number(root, "", "maxExportedPosition"),
          number(root, "", "lastFollowupEventPosition"),
          uuid(root, "history"),
          files,
          instant(root, "createdAt"));
    } catch (final IllegalArgumentException e) {
      throw new InvalidSnapshotDescriptorException(
          "the document", "a valid snapshot descriptor", shown(json), e);
    }
  }

  private static String text(final JsonNode node, final String path, final String field)
      throws InvalidSnapshotDescriptorException {
    final var value = node.get(field);
    if (value == null || !value.isTextual()) {
      throw invalid(path(path, field), "text", value);
    }
    return value.textValue();
  }

  private static long number(final JsonNode node, final String path, final String field)
      throws InvalidSnapshotDescriptorException {
    final var value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
      throw invalid(path(path, field), "a 64-bit integer", value);
    }
    return value.longValue();
  }

  private static UUID uuid(final JsonNode node, final String field)
      throws InvalidSnapshotDescriptorException {
    final var value = node.get(field);
    if (value == null || !value.isTextual() || !UUID_TEXT.matcher(value.textValue()).matches()) {
      throw invalid(field, "a UUID", value);
    }
    return UUID.fromString(value.textValue());
  }

  private static Instant instant(final JsonNode node, final String field)
      throws InvalidSnapshotDescriptorException {
    final var text = text(node, "", field);
    try {
      return Instant.parse(text);
    } catch (final DateTimeParseException e) {
      throw new InvalidSnapshotDescriptorException(field, "an ISO-8601 instant", text, e);
    }
  }

  private static String path(final String path, final String field) {
    return path.isEmpty() ? field : path + "." + field;
  }

  private static InvalidSnapshotDescriptorException invalid(
      final String field, final String expected, @Nullable final JsonNode found) {
    return new InvalidSnapshotDescriptorException(
        field, expected, found == null ? "nothing" : shown(found.toString()));
  }

  private static String shown(final byte[] json) {
    return shown(new String(json, StandardCharsets.UTF_8));
  }

  private static String shown(final String text) {
    return text.length() <= MAX_SHOWN ? text : text.substring(0, MAX_SHOWN) + "…";
  }

  /**
   * A file of a snapshot.
   *
   * @param name its name, one segment of a key
   * @param size its size in bytes
   */
  public record SnapshotFile(String name, long size) {

    public SnapshotFile {
      if (name.isEmpty() || name.contains("/")) {
        throw new IllegalArgumentException(
            "Expected a snapshot file's name to be one non-empty name, but got '%s'"
                .formatted(name));
      }
      if (size < 0) {
        throw new IllegalArgumentException(
            "Expected a snapshot file's size to be 0 or more, but got " + size);
      }
    }
  }
}

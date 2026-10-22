/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.backup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a backup of a partition needs of the store, which it doesn't copy: the entries from {@code
 * firstIndex} to {@code lastIndex} on the path of {@code history}, and the snapshot {@code
 * snapshotId}. Written before the backup is saved, and deleted after it is deleted:
 *
 * <pre>{@code
 * {"version":1,"checkpointId":17,"checkpointPosition":1052032,"history":"…",
 *  "firstIndex":1090001,"lastIndex":1096704,"snapshotId":"…","createdAt":"2026-10-21T12:00:00Z"}
 * }</pre>
 *
 * @param version the object's version, 1
 * @param checkpointId the backup's checkpoint id
 * @param checkpointPosition the position of the backup's checkpoint record
 * @param history the history the partition's leader published in when the backup was taken, whose
 *     path through the partition holds the backup's log
 * @param firstIndex the first index of the segment that holds the backup's first log position
 * @param lastIndex the last index the path serves of the segment that holds the checkpoint position
 * @param snapshotId the backup's snapshot in the store's snapshots, or null if it has none
 * @param createdAt when it was written
 */
public record BackupLog(
    int version,
    long checkpointId,
    long checkpointPosition,
    UUID history,
    long firstIndex,
    long lastIndex,
    @Nullable String snapshotId,
    Instant createdAt) {

  /** The version this class writes and reads. */
  public static final int VERSION = 1;

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern UUID_TEXT =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final int MAX_SHOWN = 200;

  public BackupLog {
    if (version != VERSION) {
      throw new IllegalArgumentException(
          "Expected backup log version %d, but got %d".formatted(VERSION, version));
    }
    if (checkpointId < 0 || checkpointPosition < 1) {
      throw new IllegalArgumentException(
          "Expected a checkpoint id of 0 or more and a checkpoint position of 1 or more, but got %d and %d"
              .formatted(checkpointId, checkpointPosition));
    }
    if (firstIndex < 1 || lastIndex < firstIndex) {
      throw new IllegalArgumentException(
          "Expected a first index of 1 or more, and a last index at or after it, but got %d and %d"
              .formatted(firstIndex, lastIndex));
    }
    if (snapshotId != null && snapshotId.isEmpty()) {
      throw new IllegalArgumentException("Expected a snapshot id or none, but got an empty one");
    }
  }

  /** Whether the backup needs the entry at the index of its history's path. */
  public boolean needs(final long index) {
    return firstIndex <= index && index <= lastIndex;
  }

  /** The object as JSON, in the contract's field order. */
  public byte[] toJson() {
    final var root = MAPPER.createObjectNode();
    root.put("version", version);
    root.put("checkpointId", checkpointId);
    root.put("checkpointPosition", checkpointPosition);
    root.put("history", history.toString());
    root.put("firstIndex", firstIndex);
    root.put("lastIndex", lastIndex);
    root.put("snapshotId", snapshotId);
    root.put("createdAt", createdAt.toString());
    try {
      return MAPPER.writeValueAsBytes(root);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads a backup's log. Fields that it doesn't know are skipped.
   *
   * @throws InvalidBackupLogException if the JSON isn't that of a valid backup log
   * @throws IOException if the bytes aren't JSON
   */
  public static BackupLog fromJson(final byte[] json) throws IOException {
    final var root = MAPPER.readTree(json);
    if (root == null || !root.isObject()) {
      throw new InvalidBackupLogException("the document", "a JSON object", shown(json));
    }
    final long version = number(root, "version");
    if (version != VERSION) {
      throw invalid("version", String.valueOf(VERSION), root.get("version"));
    }
    try {
      return new BackupLog(
          VERSION,
          number(root, "checkpointId"),
          number(root, "checkpointPosition"),
          uuid(root, "history"),
          number(root, "firstIndex"),
          number(root, "lastIndex"),
          optionalText(root, "snapshotId"),
          instant(root, "createdAt"));
    } catch (final IllegalArgumentException e) {
      throw new InvalidBackupLogException("the document", "a valid backup log", shown(json), e);
    }
  }

  private static long number(final JsonNode node, final String field)
      throws InvalidBackupLogException {
    final var value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
      throw invalid(field, "a 64-bit integer", value);
    }
    return value.longValue();
  }

  private static @Nullable String optionalText(final JsonNode node, final String field)
      throws InvalidBackupLogException {
    final var value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isTextual()) {
      throw invalid(field, "text or null", value);
    }
    return value.textValue();
  }

  private static UUID uuid(final JsonNode node, final String field)
      throws InvalidBackupLogException {
    final var value = node.get(field);
    if (value == null || !value.isTextual() || !UUID_TEXT.matcher(value.textValue()).matches()) {
      throw invalid(field, "a UUID", value);
    }
    return UUID.fromString(value.textValue());
  }

  private static Instant instant(final JsonNode node, final String field)
      throws InvalidBackupLogException {
    final var value = node.get(field);
    if (value == null || !value.isTextual()) {
      throw invalid(field, "text", value);
    }
    try {
      return Instant.parse(value.textValue());
    } catch (final DateTimeParseException e) {
      throw new InvalidBackupLogException(field, "an ISO-8601 instant", value.textValue(), e);
    }
  }

  private static InvalidBackupLogException invalid(
      final String field, final String expected, @Nullable final JsonNode found) {
    return new InvalidBackupLogException(
        field, expected, found == null ? "nothing" : shown(found.toString()));
  }

  private static String shown(final byte[] json) {
    return shown(new String(json, StandardCharsets.UTF_8));
  }

  private static String shown(final String text) {
    return text.length() <= MAX_SHOWN ? text : text.substring(0, MAX_SHOWN) + "…";
  }
}

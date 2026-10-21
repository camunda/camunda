/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import io.camunda.zeebe.eventplane.segment.LogId;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The keys of the Event Plane's objects, and the prefixes to list them by. A {@link LogId} is a
 * partition of a physical tenant of a cluster. Names that a user chooses, such as a cluster, a
 * tenant, a consumer group or a worker, are percent-encoded so that none can add a path segment:
 * every byte outside {@code A-Za-z0-9-._~} becomes {@code %XX}, and {@code .} and {@code ..} become
 * {@code %2E} and {@code %2E%2E}. Indexes are zero-padded to 20 digits, so that keys sort by index.
 * Cluster ids must not start with {@code _}, which is for what isn't a cluster.
 *
 * <table>
 *   <caption>The keys</caption>
 *   <tr><th>Object</th><th>Key</th></tr>
 *   <tr><td>A history's manifest part for one partition</td>
 *       <td>{@code {cluster}/{tenant}/histories/{history}/{partition}.json}</td></tr>
 *   <tr><td>A segment</td>
 *       <td>{@code {cluster}/{tenant}/{partition}/{history}/{firstIndex}-{lastIndex}-L{level}-{ulid}.seg}</td></tr>
 *   <tr><td>A retention marker</td><td>{@code {cluster}/{tenant}/{partition}/retention/{ulid}.json}</td></tr>
 *   <tr><td>A snapshot's files and descriptor</td>
 *       <td>{@code {cluster}/{tenant}/{partition}/snapshots/{snapshotId}/…}, {@code …/descriptor.json}</td></tr>
 *   <tr><td>A consumer group</td><td>{@code _groups/{group}/group.json}</td></tr>
 *   <tr><td>A worker's heartbeat</td><td>{@code _groups/{group}/workers/{worker}.json}</td></tr>
 *   <tr><td>A cursor</td>
 *       <td>{@code _groups/{group}/cursors/{cluster}/{tenant}/{partition}.json}</td></tr>
 * </table>
 */
public final class ObjectNames {

  /** The directory of consumer groups, which no cluster can be named like. */
  static final String GROUPS = "_groups/";

  private static final String UNRESERVED =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
  private static final String HEX = "0123456789ABCDEF";
  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
  private static final Pattern SEGMENT_KEY =
      Pattern.compile(
          "(?:.*/)?(%s)/(\\d{20})-(\\d{20})-L(\\d{1,9})-([0-7][0-9A-HJKMNP-TV-Z]{25})\\.seg"
              .formatted(UUID_PATTERN));
  private static final Pattern CURSOR_KEY =
      Pattern.compile("_groups/([^/]+)/cursors/([^/]+)/([^/]+)/(\\d{1,9})\\.json");

  private ObjectNames() {}

  // Keys

  /** A history's manifest part for one log's partition. */
  public static String historyPart(final LogId log, final UUID history) {
    return historiesPrefix(log.cluster(), log.tenant()) + history + "/" + log.partition() + ".json";
  }

  /** A segment of a log, in the directory of the history whose journal it holds. */
  public static String segment(
      final LogId log,
      final UUID history,
      final long firstIndex,
      final long lastIndex,
      final int level,
      final String ulid) {
    return segment(log, new SegmentName(history, firstIndex, lastIndex, level, ulid));
  }

  /** A segment of a log, in the directory of the history whose journal it holds. */
  public static String segment(final LogId log, final SegmentName name) {
    return segmentsPrefix(log, name.history())
        + "%020d-%020d-L%d-%s.seg"
            .formatted(name.firstIndex(), name.lastIndex(), name.level(), name.ulid());
  }

  /** A retention marker of a log. */
  public static String retentionMarker(final LogId log, final String ulid) {
    if (!Ulid.isValid(ulid)) {
      throw new InvalidObjectNameException("a ULID", ulid);
    }
    return retentionPrefix(log) + ulid + ".json";
  }

  /** The descriptor of a snapshot of a log, which names its files. */
  public static String snapshotDescriptor(final LogId log, final String snapshotId) {
    return snapshotFile(log, snapshotId, "descriptor.json");
  }

  /** A file of a snapshot of a log. The file name is one name, not a path. */
  public static String snapshotFile(
      final LogId log, final String snapshotId, final String fileName) {
    return snapshotPrefix(log, snapshotId) + name("a file name", fileName);
  }

  /** A consumer group. */
  public static String group(final String group) {
    return GROUPS + name("a group", group) + "/group.json";
  }

  /** A worker's heartbeat. */
  public static String worker(final String group, final String worker) {
    return workersPrefix(group) + name("a worker", worker) + ".json";
  }

  /** The cursor of a group in a log. */
  public static String cursor(final String group, final LogId log) {
    return cursorsPrefix(group)
        + cluster(log.cluster())
        + "/"
        + name("a tenant", log.tenant())
        + "/"
        + log.partition()
        + ".json";
  }

  // Prefixes

  /** The directory of a cluster, which holds its tenants'. */
  public static String clusterPrefix(final String cluster) {
    return cluster(cluster) + "/";
  }

  /** The directory of a physical tenant, which holds its partitions' and its histories'. */
  public static String tenantPrefix(final String cluster, final String tenant) {
    return clusterPrefix(cluster) + name("a tenant", tenant) + "/";
  }

  /** The directory of a tenant's histories' manifest parts: a directory for each history. */
  public static String historiesPrefix(final String cluster, final String tenant) {
    return tenantPrefix(cluster, tenant) + "histories/";
  }

  /** A log's directory: its histories' segment directories, its retention markers, snapshots. */
  public static String logPrefix(final LogId log) {
    return tenantPrefix(log.cluster(), log.tenant()) + log.partition() + "/";
  }

  /** The directory of the segments of a log that hold the journal of one history. */
  public static String segmentsPrefix(final LogId log, final UUID history) {
    return logPrefix(log) + history + "/";
  }

  /** The directory of a log's retention markers. */
  public static String retentionPrefix(final LogId log) {
    return logPrefix(log) + "retention/";
  }

  /** The directory of a log's snapshots: a directory for each. */
  public static String snapshotsPrefix(final LogId log) {
    return logPrefix(log) + "snapshots/";
  }

  /** The directory of one snapshot of a log: its files, and its descriptor. */
  public static String snapshotPrefix(final LogId log, final String snapshotId) {
    return snapshotsPrefix(log) + name("a snapshot id", snapshotId) + "/";
  }

  /** The directory of consumer groups: a directory for each. */
  public static String groupsPrefix() {
    return GROUPS;
  }

  /** The directory of a group's workers' heartbeats. */
  public static String workersPrefix(final String group) {
    return GROUPS + name("a group", group) + "/workers/";
  }

  /** The directory of a group's cursors: a directory for each cluster. */
  public static String cursorsPrefix(final String group) {
    return GROUPS + name("a group", group) + "/cursors/";
  }

  // Parsers

  /**
   * Reads the name of a segment from its key. Only the last two parts count, the history's
   * directory and the segment's name, so a key below any root will do.
   *
   * @throws InvalidObjectNameException if the key isn't that of a segment
   */
  public static SegmentName parseSegment(final String key) {
    final var matcher = SEGMENT_KEY.matcher(key);
    if (!matcher.matches()) {
      throw new InvalidObjectNameException(
          "a segment key, ending in {history}/{firstIndex}-{lastIndex}-L{level}-{ulid}.seg", key);
    }
    try {
      return new SegmentName(
          UUID.fromString(matcher.group(1)),
          Long.parseLong(matcher.group(2)),
          Long.parseLong(matcher.group(3)),
          Integer.parseInt(matcher.group(4)),
          matcher.group(5));
    } catch (final IllegalArgumentException e) {
      throw new InvalidObjectNameException("a segment key of valid indexes and ULID", key);
    }
  }

  /**
   * Reads the group, cluster, tenant and partition from a cursor's key.
   *
   * @throws InvalidObjectNameException if the key isn't that of a cursor
   */
  public static CursorName parseCursor(final String key) {
    final var matcher = CURSOR_KEY.matcher(key);
    if (!matcher.matches()) {
      throw new InvalidObjectNameException(
          "a cursor key, _groups/{group}/cursors/{cluster}/{tenant}/{partition}.json", key);
    }
    final var cluster = decode(matcher.group(2));
    if (cluster.startsWith("_")) {
      throw new InvalidObjectNameException("a cluster id that doesn't start with '_'", key);
    }
    final int partition = Integer.parseInt(matcher.group(4));
    if (partition < 1 || !String.valueOf(partition).equals(matcher.group(4))) {
      throw new InvalidObjectNameException("a partition of 1 or more, without leading zeros", key);
    }
    final var tenant = decode(matcher.group(3));
    if (cluster.isBlank() || tenant.isBlank()) {
      throw new InvalidObjectNameException("a cluster and a tenant that aren't blank", key);
    }
    return new CursorName(decode(matcher.group(1)), new LogId(cluster, tenant, partition));
  }

  // Encoding

  /**
   * Percent-encodes a name for a key: every byte of its UTF-8 encoding outside {@code
   * A-Za-z0-9-._~} as {@code %XX}, and {@code .} and {@code ..} as {@code %2E} and {@code %2E%2E},
   * which would navigate otherwise.
   */
  public static String encode(final String name) {
    if (".".equals(name) || "..".equals(name)) {
      return name.replace(".", "%2E");
    }
    final var encoded = new StringBuilder();
    for (final byte b : name.getBytes(StandardCharsets.UTF_8)) {
      if (UNRESERVED.indexOf(b) >= 0) {
        encoded.append((char) b);
      } else {
        encoded.append('%').append(HEX.charAt(b >> 4 & 15)).append(HEX.charAt(b & 15));
      }
    }
    return encoded.toString();
  }

  /**
   * Reads a name that {@link #encode} made, as in a listing of prefixes.
   *
   * @throws InvalidObjectNameException if {@code encoded} isn't what {@code encode} makes of any
   *     name
   */
  public static String decode(final String encoded) {
    final var bytes = new ByteArrayOutputStream(encoded.length());
    for (int i = 0; i < encoded.length(); i++) {
      final char c = encoded.charAt(i);
      if (c == '%') {
        final int high = i + 2 < encoded.length() ? hexDigit(encoded.charAt(i + 1)) : -1;
        final int low = high < 0 ? -1 : hexDigit(encoded.charAt(i + 2));
        if (low < 0) {
          throw invalidEncoding(encoded);
        }
        bytes.write(high << 4 | low);
        i += 2;
      } else if (c < 0x80) {
        bytes.write(c);
      } else {
        throw invalidEncoding(encoded);
      }
    }
    final String name;
    try {
      name =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes.toByteArray()))
              .toString();
    } catch (final CharacterCodingException e) {
      throw invalidEncoding(encoded);
    }
    if (!encode(name).equals(encoded)) {
      throw invalidEncoding(encoded);
    }
    return name;
  }

  private static InvalidObjectNameException invalidEncoding(final String encoded) {
    return new InvalidObjectNameException(
        "a name in percent-encoded UTF-8, with every byte outside A-Za-z0-9-._~ as %XX", encoded);
  }

  private static int hexDigit(final char c) {
    return HEX.indexOf(Character.toUpperCase(c));
  }

  private static String cluster(final String cluster) {
    if (cluster.startsWith("_")) {
      throw new InvalidObjectNameException("a cluster id that doesn't start with '_'", cluster);
    }
    return name("a cluster id", cluster);
  }

  private static String name(final String what, final String name) {
    if (name.isEmpty()) {
      throw new InvalidObjectNameException(what + " of 1 or more characters", name);
    }
    return encode(name);
  }
}

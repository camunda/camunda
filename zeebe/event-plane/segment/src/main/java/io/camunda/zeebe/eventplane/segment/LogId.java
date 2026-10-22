/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidMetadata;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * A log: a partition of a physical tenant of a cluster. Its text is {@code
 * {cluster}/{tenant}/{partition}}, with the plain names, not percent-encoded as in object keys. The
 * metadata of each of the log's segments holds it.
 *
 * @param cluster the cluster's id, not blank
 * @param tenant the physical tenant, {@code default} for the default one, not blank
 * @param partition the partition, 1 or more
 */
public record LogId(String cluster, String tenant, int partition) {

  private static final int MAX_LENGTH = 0xFFFF;

  public LogId {
    if (cluster.isBlank()) {
      throw new InvalidMetadata("Expected a cluster id, but got '%s'".formatted(cluster));
    }
    if (tenant.isBlank()) {
      throw new InvalidMetadata("Expected a tenant id, but got '%s'".formatted(tenant));
    }
    if (utf8(cluster).length > MAX_LENGTH) {
      throw new InvalidMetadata(
          "Expected a cluster id of at most %d UTF-8 bytes, but got %d"
              .formatted(MAX_LENGTH, utf8(cluster).length));
    }
    if (utf8(tenant).length > MAX_LENGTH) {
      throw new InvalidMetadata(
          "Expected a tenant id of at most %d UTF-8 bytes, but got %d"
              .formatted(MAX_LENGTH, utf8(tenant).length));
    }
    if (partition < 1) {
      throw new InvalidMetadata("Expected a partition of at least 1, but got " + partition);
    }
  }

  /**
   * Reads {@code {cluster}/{tenant}/{partition}}, as {@link #toString()} writes it. A log whose
   * cluster or tenant contains a slash can't be read back.
   *
   * @throws InvalidLogIdException if the text isn't that, or names a cluster, a tenant or a
   *     partition that no log has
   */
  public static LogId parse(final String text) {
    final var parts = text.split("/", -1);
    if (parts.length != 3) {
      throw new InvalidLogIdException(text, null);
    }
    try {
      return new LogId(parts[0], parts[1], Integer.parseInt(parts[2]));
    } catch (final NumberFormatException | InvalidMetadata e) {
      throw new InvalidLogIdException(text, e);
    }
  }

  @Override
  public String toString() {
    return cluster + "/" + tenant + "/" + partition;
  }

  byte[] encode() {
    final var clusterBytes = utf8(cluster);
    final var tenantBytes = utf8(tenant);
    return ByteBuffer.allocate(2 + clusterBytes.length + 2 + tenantBytes.length + 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putShort((short) clusterBytes.length)
        .put(clusterBytes)
        .putShort((short) tenantBytes.length)
        .put(tenantBytes)
        .putInt(partition)
        .array();
  }

  static LogId decode(ByteBuffer value) {
    final int length = value.remaining();
    final String cluster;
    final String tenant;
    final int partition;
    try {
      cluster = string(value, "cluster id");
      tenant = string(value, "tenant id");
      partition = value.getInt();
    } catch (BufferUnderflowException e) {
      throw new InvalidMetadata(
          ("Expected a log id with a cluster id, a tenant id and a partition, but the"
                  + " metadata entry ends after %d bytes")
              .formatted(length),
          e);
    }
    if (value.hasRemaining()) {
      throw new InvalidMetadata(
          "Expected a log id of %d bytes, but the metadata entry has %d"
              .formatted(value.position(), length));
    }
    return new LogId(cluster, tenant, partition);
  }

  private static String string(ByteBuffer value, String name) {
    final var bytes = new byte[Short.toUnsignedInt(value.getShort())];
    value.get(bytes);
    try {
      // Unlike new String(bytes, UTF_8), which replaces malformed bytes with U+FFFD.
      return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    } catch (CharacterCodingException e) {
      throw new InvalidMetadata(
          "Expected a log id's ids in UTF-8, but the %s is %s"
              .formatted(name, HexFormat.of().formatHex(bytes)),
          e);
    }
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}

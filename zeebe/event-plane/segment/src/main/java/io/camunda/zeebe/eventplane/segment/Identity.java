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

/** The log a segment belongs to. */
public record Identity(String clusterId, String tenantId, int partition) {

  private static final int MAX_LENGTH = 0xFFFF;

  public Identity {
    if (clusterId.isBlank()) {
      throw new InvalidMetadata("Expected a cluster id, but got '%s'".formatted(clusterId));
    }
    if (tenantId.isBlank()) {
      throw new InvalidMetadata("Expected a tenant id, but got '%s'".formatted(tenantId));
    }
    if (utf8(clusterId).length > MAX_LENGTH) {
      throw new InvalidMetadata(
          "Expected a cluster id of at most %d UTF-8 bytes, but got %d"
              .formatted(MAX_LENGTH, utf8(clusterId).length));
    }
    if (utf8(tenantId).length > MAX_LENGTH) {
      throw new InvalidMetadata(
          "Expected a tenant id of at most %d UTF-8 bytes, but got %d"
              .formatted(MAX_LENGTH, utf8(tenantId).length));
    }
    if (partition < 1) {
      throw new InvalidMetadata("Expected a partition of at least 1, but got " + partition);
    }
  }

  byte[] encode() {
    final var cluster = utf8(clusterId);
    final var tenant = utf8(tenantId);
    return ByteBuffer.allocate(2 + cluster.length + 2 + tenant.length + 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putShort((short) cluster.length)
        .put(cluster)
        .putShort((short) tenant.length)
        .put(tenant)
        .putInt(partition)
        .array();
  }

  static Identity decode(ByteBuffer value) {
    final int length = value.remaining();
    final String clusterId;
    final String tenantId;
    final int partition;
    try {
      clusterId = string(value, "cluster id");
      tenantId = string(value, "tenant id");
      partition = value.getInt();
    } catch (BufferUnderflowException e) {
      throw new InvalidMetadata(
          ("Expected an identity with a cluster id, a tenant id and a partition, but the"
                  + " metadata entry ends after %d bytes")
              .formatted(length),
          e);
    }
    if (value.hasRemaining()) {
      throw new InvalidMetadata(
          "Expected an identity of %d bytes, but the metadata entry has %d"
              .formatted(value.position(), length));
    }
    return new Identity(clusterId, tenantId, partition);
  }

  private static String string(ByteBuffer value, String name) {
    final var bytes = new byte[Short.toUnsignedInt(value.getShort())];
    value.get(bytes);
    try {
      // Unlike new String(bytes, UTF_8), which replaces malformed bytes with U+FFFD.
      return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    } catch (CharacterCodingException e) {
      throw new InvalidMetadata(
          "Expected an identity's ids in UTF-8, but the %s is %s"
              .formatted(name, HexFormat.of().formatHex(bytes)),
          e);
    }
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}

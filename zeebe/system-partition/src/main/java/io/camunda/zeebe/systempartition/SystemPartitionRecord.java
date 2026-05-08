/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Wire format for an Update entry written to the system-partition Raft log.
 *
 * <p>Layout: 8-byte big-endian {@code expectedPreviousVersion}, followed by the proto-encoded
 * {@link ClusterConfiguration} produced by {@link ProtoBufSerializer#encode(ClusterConfiguration)}.
 *
 * <p>{@code expectedPreviousVersion} is checked at apply time as a CAS guard. A mismatch means the
 * appender saw a stale state at write time; the entry is committed but not applied to in-memory
 * state, and the pending future fails.
 */
public record SystemPartitionRecord(
    long expectedPreviousVersion, ClusterConfiguration newConfiguration) {

  private static final int VERSION_HEADER_BYTES = Long.BYTES;
  private static final ProtoBufSerializer SERIALIZER = new ProtoBufSerializer();

  public ByteBuffer encode() {
    final byte[] encodedConfig = SERIALIZER.encode(newConfiguration);
    final ByteBuffer buffer =
        ByteBuffer.allocate(VERSION_HEADER_BYTES + encodedConfig.length)
            .order(ByteOrder.BIG_ENDIAN);
    buffer.putLong(expectedPreviousVersion);
    buffer.put(encodedConfig);
    buffer.flip();
    return buffer;
  }

  public static SystemPartitionRecord decode(final byte[] bytes) {
    return decode(bytes, 0, bytes.length);
  }

  public static SystemPartitionRecord decode(
      final byte[] bytes, final int offset, final int length) {
    if (length < VERSION_HEADER_BYTES) {
      throw new IllegalArgumentException(
          "Encoded SystemPartitionRecord shorter than %d bytes (got %d)"
              .formatted(VERSION_HEADER_BYTES, length));
    }
    final ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, length).order(ByteOrder.BIG_ENDIAN);
    final long expectedPreviousVersion = buffer.getLong();
    final ClusterConfiguration newConfig =
        SERIALIZER.decodeClusterTopology(
            bytes, offset + VERSION_HEADER_BYTES, length - VERSION_HEADER_BYTES);
    return new SystemPartitionRecord(expectedPreviousVersion, newConfig);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import java.nio.ByteOrder;
import java.util.Arrays;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The changelog's offset-marker record (ADR 0009 Decisions 1/6): a reserved key, strictly the last
 * record of every cut's changelog output, whose value carries the cut's source offset — the
 * failover/rebuild resume token ("read the latest marker, resume the source at X+1").
 *
 * <p><b>Reserved key.</b> Every real changelog key is a store row key: at least 4 bytes (a {@code
 * GroupedCellStore} meta row — the bare big-endian {@code group}) and group ids are allocated
 * monotonically from {@code 0}, never reused (see {@code GroupedCellStore}'s javadoc). The marker's
 * key is the 4-byte big-endian sentinel {@code -1} ({@code 0xFFFFFFFF}) — outside that allocation
 * range by construction, so it can never collide with a real group's meta or cell row. <b>The byte
 * layout is durable identity — never change it</b>: changing this constant orphans every unread
 * marker in a live changelog.
 *
 * <p><b>Marker value.</b> {@code version(1 byte) ++ sourceOffset(8 bytes, big-endian)}. The version
 * byte lets a future format (e.g. carrying a fencing epoch once ADR 0009's deferred Decision 4
 * stamping lands) evolve without breaking a reader of unstamped history — an old marker simply
 * falls outside the new format's memory, exactly as the ADR's deferred-fencing note describes.
 */
public final class ChangelogMarker {

  /** Marker value format version 1: {@code version ++ sourceOffset}, no fencing epoch. */
  public static final byte VERSION_1 = 1;

  /** The reserved group sentinel a marker key encodes — outside any real group's allocation. */
  private static final int RESERVED_GROUP = -1;

  private static final int VALUE_LENGTH = Byte.BYTES + Long.BYTES;

  /** The reserved, collision-proof marker key: the 4-byte big-endian sentinel group {@code -1}. */
  public static final byte[] KEY = encodeKey();

  private ChangelogMarker() {}

  private static byte[] encodeKey() {
    final byte[] key = new byte[Integer.BYTES];
    new UnsafeBuffer(key).putInt(0, RESERVED_GROUP, ByteOrder.BIG_ENDIAN);
    return key;
  }

  /** Encodes the marker value for {@code sourceOffset} in the current format version. */
  public static byte[] encodeValue(final long sourceOffset) {
    final byte[] value = new byte[VALUE_LENGTH];
    final UnsafeBuffer buffer = new UnsafeBuffer(value);
    buffer.putByte(0, VERSION_1);
    buffer.putLong(Byte.BYTES, sourceOffset, ByteOrder.BIG_ENDIAN);
    return value;
  }

  /** Decodes a marker value's source offset (format version 1). */
  public static long decodeSourceOffset(final byte[] value) {
    if (value.length != VALUE_LENGTH || value[0] != VERSION_1) {
      throw new IllegalArgumentException(
          "unsupported changelog marker value: expected version " + VERSION_1);
    }
    return new UnsafeBuffer(value).getLong(Byte.BYTES, ByteOrder.BIG_ENDIAN);
  }

  /** Whether {@code key} is the reserved marker key. */
  public static boolean isMarkerKey(final byte[] key) {
    return Arrays.equals(key, KEY);
  }
}

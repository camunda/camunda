/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.msgpack.spec.MsgPackCodes;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Navigates a MsgPack document along the segments of a {@code '.'}-separated variable mapping
 * target path, mirroring how a FEEL path expression (e.g. {@code a.b.c}) resolves: descending into
 * nested maps by key. Used to look up the existing scope value that a nested output mapping target
 * merges with.
 */
@NullMarked
public final class MsgPackPath {

  private MsgPackPath() {}

  /**
   * Returns the value of {@code document} at {@code path.subList(fromIndex, path.size())}, or
   * {@code null} if any segment is missing or an intermediate value is not a map. The returned
   * buffer is a view into {@code document} — callers must copy it if they retain it.
   *
   * @param document the MsgPack document to navigate (the value of the path's root variable)
   * @param path the full target path; segments before {@code fromIndex} are already consumed
   * @param fromIndex the index of the first segment to resolve within {@code document}
   * @return the value at the path, or {@code null} if it cannot be resolved
   */
  public static @Nullable DirectBuffer navigate(
      final DirectBuffer document, final List<String> path, final int fromIndex) {
    var current = document;
    final var reader = new MsgPackReader();
    for (int i = fromIndex; i < path.size(); i++) {
      current = valueOfKey(reader, current, path.get(i));
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  private static @Nullable DirectBuffer valueOfKey(
      final MsgPackReader reader, final DirectBuffer map, final String key) {
    if (map.capacity() == 0 || !MsgPackCodes.isMap(map.getByte(0))) {
      return null;
    }
    reader.wrap(map, 0, map.capacity());
    final int entries = reader.readMapHeader();
    final var keyBuffer = BufferUtil.wrapString(key);
    for (int i = 0; i < entries; i++) {
      final var keyToken = reader.readToken();
      final var isMatch =
          keyToken.getType() == MsgPackType.STRING
              && BufferUtil.equals(keyToken.getValueBuffer(), keyBuffer);
      final int valueOffset = reader.getOffset();
      reader.skipValue();
      if (isMatch) {
        return new UnsafeBuffer(map, valueOffset, reader.getOffset() - valueOffset);
      }
    }
    return null;
  }
}

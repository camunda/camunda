/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.camunda.analytics.lake.state.TranslatorState.ObjectSighting;
import io.camunda.analytics.lake.state.TranslatorState.ObjectSightingList;
import io.camunda.zeebe.db.DbValue;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * ZeebeDb value flyweight for an {@link ObjectSightingList}: {@code count(2, unsigned) ++
 * overflowed(1) ++ [objectType, objectId, scopeKey(8)][count]} — {@code objectType}/{@code
 * objectId} are length-prefixed UTF-8 (see {@link LakeValueCodec}), mirroring {@link
 * VariantAccumulatorValue}'s own count-prefixed-array layout.
 */
final class ObjectSightingListValue implements DbValue {

  /** {@code count} is a 16-bit unsigned field (see class javadoc) — this is its capacity. */
  private static final int MAX_COUNT = 0xFFFF;

  private static final byte[][] EMPTY_UTF8 = new byte[0][];
  private static final long[] EMPTY_LONGS = new long[0];

  private byte[][] objectTypeUtf8 = EMPTY_UTF8;
  private byte[][] objectIdUtf8 = EMPTY_UTF8;
  private long[] scopeKeys = EMPTY_LONGS;
  private boolean overflowed;

  ObjectSightingListValue set(final ObjectSightingList list) {
    final List<ObjectSighting> sightings = list.sightings();
    if (sightings.size() > MAX_COUNT) {
      // Never expected in practice: MAX_OBJECT_SIGHTINGS_PER_INSTANCE (LakeTranslator's own soft
      // cap) is far below this encoding's hard 16-bit ceiling -- fail loudly rather than silently
      // truncate, which would corrupt the relations derivation.
      throw new IllegalStateException(
          "object sighting list size "
              + sightings.size()
              + " exceeds the 16-bit count field's capacity of "
              + MAX_COUNT);
    }
    objectTypeUtf8 = new byte[sightings.size()][];
    objectIdUtf8 = new byte[sightings.size()][];
    scopeKeys = new long[sightings.size()];
    for (int i = 0; i < sightings.size(); i++) {
      final ObjectSighting sighting = sightings.get(i);
      objectTypeUtf8[i] = sighting.objectType().getBytes(UTF_8);
      objectIdUtf8[i] = sighting.objectId().getBytes(UTF_8);
      scopeKeys[i] = sighting.scopeKey();
    }
    overflowed = list.overflowed();
    return this;
  }

  ObjectSightingList toRecord() {
    final List<ObjectSighting> sightings = new ArrayList<>(objectTypeUtf8.length);
    for (int i = 0; i < objectTypeUtf8.length; i++) {
      sightings.add(
          new ObjectSighting(
              new String(objectTypeUtf8[i], UTF_8),
              new String(objectIdUtf8[i], UTF_8),
              scopeKeys[i]));
    }
    return new ObjectSightingList(sightings, overflowed);
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    final int count = buffer.getShort(offset) & 0xFFFF;
    offset += Short.BYTES;
    overflowed = buffer.getByte(offset) != 0;
    offset += Byte.BYTES;
    final byte[][] types = new byte[count][];
    final byte[][] ids = new byte[count][];
    final long[] scopes = new long[count];
    for (int i = 0; i < count; i++) {
      types[i] = LakeValueCodec.getBytes(buffer, offset);
      offset += LakeValueCodec.sizeOf(types[i]);
      ids[i] = LakeValueCodec.getBytes(buffer, offset);
      offset += LakeValueCodec.sizeOf(ids[i]);
      scopes[i] = buffer.getLong(offset);
      offset += Long.BYTES;
    }
    objectTypeUtf8 = types;
    objectIdUtf8 = ids;
    scopeKeys = scopes;
  }

  @Override
  public int getLength() {
    int length = Short.BYTES + Byte.BYTES;
    for (int i = 0; i < objectTypeUtf8.length; i++) {
      length +=
          LakeValueCodec.sizeOf(objectTypeUtf8[i])
              + LakeValueCodec.sizeOf(objectIdUtf8[i])
              + Long.BYTES;
    }
    return length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    final int start = offset;
    buffer.putShort(offset, (short) objectTypeUtf8.length);
    offset += Short.BYTES;
    buffer.putByte(offset, (byte) (overflowed ? 1 : 0));
    offset += Byte.BYTES;
    for (int i = 0; i < objectTypeUtf8.length; i++) {
      offset = LakeValueCodec.putBytes(buffer, offset, objectTypeUtf8[i]);
      offset = LakeValueCodec.putBytes(buffer, offset, objectIdUtf8[i]);
      buffer.putLong(offset, scopeKeys[i]);
      offset += Long.BYTES;
    }
    return offset - start;
  }
}

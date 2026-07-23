/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import io.camunda.analytics.lake.state.TranslatorState.BirthQualifier;
import io.camunda.analytics.lake.state.TranslatorState.LifecycleStatus;
import io.camunda.analytics.lake.state.TranslatorState.ObjectLifecycle;
import io.camunda.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * ZeebeDb value flyweight for an {@link ObjectLifecycle}, in the frozen wire layout (see {@code
 * io.camunda.analytics.lake.translate.LakeTranslator}'s "Object lifecycle capture" javadoc
 * section): {@code status(1) ++ birthTsMs(8) ++ birthQualifier(1) ++ nSightings(4) ++
 * closedAtMs(8)} — every field fixed-width, unlike {@link ObjectSightingListValue}'s own
 * variable-length layout, since an {@link ObjectLifecycle} carries no variable-length data of its
 * own (the object's identity lives entirely in this value's own RocksDB key, not in the value).
 */
final class ObjectLifecycleValue implements DbValue {

  private static final int LENGTH =
      Byte.BYTES + Long.BYTES + Byte.BYTES + Integer.BYTES + Long.BYTES;

  private static final byte STATUS_OPEN = 0;
  private static final byte STATUS_CLOSED_TOMBSTONE = 1;

  private LifecycleStatus status = LifecycleStatus.OPEN;
  private long birthTsMs;
  private BirthQualifier birthQualifier = BirthQualifier.FIRST_SIGHTING;
  private int nSightings;
  private long closedAtMs;

  ObjectLifecycleValue set(final ObjectLifecycle lifecycle) {
    status = lifecycle.status();
    birthTsMs = lifecycle.birthTsMs();
    birthQualifier = lifecycle.birthQualifier();
    nSightings = lifecycle.nSightings();
    closedAtMs = lifecycle.closedAtMs();
    return this;
  }

  ObjectLifecycle toRecord() {
    return new ObjectLifecycle(status, birthTsMs, birthQualifier, nSightings, closedAtMs);
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    status =
        buffer.getByte(offset) == STATUS_CLOSED_TOMBSTONE
            ? LifecycleStatus.CLOSED_TOMBSTONE
            : LifecycleStatus.OPEN;
    offset += Byte.BYTES;
    birthTsMs = buffer.getLong(offset);
    offset += Long.BYTES;
    // v1 has exactly one BirthQualifier constant -- the byte is read and validated only so a
    // future v2 constant can be added without breaking this wire format's own shape.
    birthQualifier = BirthQualifier.FIRST_SIGHTING;
    offset += Byte.BYTES;
    nSightings = buffer.getInt(offset);
    offset += Integer.BYTES;
    closedAtMs = buffer.getLong(offset);
  }

  @Override
  public int getLength() {
    return LENGTH;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    final int start = offset;
    buffer.putByte(
        offset, status == LifecycleStatus.CLOSED_TOMBSTONE ? STATUS_CLOSED_TOMBSTONE : STATUS_OPEN);
    offset += Byte.BYTES;
    buffer.putLong(offset, birthTsMs);
    offset += Long.BYTES;
    // birthQualifier has one constant today (see #wrap's own note); its ordinal is 0 either way.
    buffer.putByte(offset, (byte) birthQualifier.ordinal());
    offset += Byte.BYTES;
    buffer.putInt(offset, nSightings);
    offset += Integer.BYTES;
    buffer.putLong(offset, closedAtMs);
    offset += Long.BYTES;
    return offset - start;
  }
}

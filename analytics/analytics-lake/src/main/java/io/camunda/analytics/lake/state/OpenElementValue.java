/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.camunda.analytics.lake.state.TranslatorState.OpenElement;
import io.camunda.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * ZeebeDb value flyweight for an {@link OpenElement}. UTF-8 string bytes are cached on {@link #set}
 * so {@link #getLength()} and {@link #write} — called in that order by ZeebeDb — agree on the
 * serialized size without re-encoding.
 */
final class OpenElementValue implements DbValue {

  private static final byte[] EMPTY = new byte[0];

  private long instanceKey;
  private int version;
  private long startMs;
  private long instanceStartMs;
  private byte[] processId = EMPTY;
  private byte[] tenantId = EMPTY;
  private byte[] elementId = EMPTY;
  private byte[] elementType = EMPTY;

  OpenElementValue set(final OpenElement element) {
    instanceKey = element.instanceKey();
    version = element.version();
    startMs = element.startMs();
    instanceStartMs = element.instanceStartMs();
    processId = element.processId().getBytes(UTF_8);
    tenantId = element.tenantId().getBytes(UTF_8);
    elementId = element.elementId().getBytes(UTF_8);
    elementType = element.elementType().getBytes(UTF_8);
    return this;
  }

  OpenElement toRecord() {
    return new OpenElement(
        instanceKey,
        new String(processId, UTF_8),
        version,
        new String(tenantId, UTF_8),
        new String(elementId, UTF_8),
        new String(elementType, UTF_8),
        startMs,
        instanceStartMs);
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    instanceKey = buffer.getLong(offset);
    offset += Long.BYTES;
    version = buffer.getInt(offset);
    offset += Integer.BYTES;
    startMs = buffer.getLong(offset);
    offset += Long.BYTES;
    instanceStartMs = buffer.getLong(offset);
    offset += Long.BYTES;
    processId = LakeValueCodec.getBytes(buffer, offset);
    offset += LakeValueCodec.sizeOf(processId);
    tenantId = LakeValueCodec.getBytes(buffer, offset);
    offset += LakeValueCodec.sizeOf(tenantId);
    elementId = LakeValueCodec.getBytes(buffer, offset);
    offset += LakeValueCodec.sizeOf(elementId);
    elementType = LakeValueCodec.getBytes(buffer, offset);
  }

  @Override
  public int getLength() {
    return Long.BYTES
        + Integer.BYTES
        + Long.BYTES
        + Long.BYTES
        + LakeValueCodec.sizeOf(processId)
        + LakeValueCodec.sizeOf(tenantId)
        + LakeValueCodec.sizeOf(elementId)
        + LakeValueCodec.sizeOf(elementType);
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, instanceKey);
    offset += Long.BYTES;
    buffer.putInt(offset, version);
    offset += Integer.BYTES;
    buffer.putLong(offset, startMs);
    offset += Long.BYTES;
    buffer.putLong(offset, instanceStartMs);
    offset += Long.BYTES;
    offset = LakeValueCodec.putBytes(buffer, offset, processId);
    offset = LakeValueCodec.putBytes(buffer, offset, tenantId);
    offset = LakeValueCodec.putBytes(buffer, offset, elementId);
    LakeValueCodec.putBytes(buffer, offset, elementType);
    return getLength();
  }
}

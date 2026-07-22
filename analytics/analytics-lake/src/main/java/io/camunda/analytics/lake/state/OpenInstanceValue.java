/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.camunda.analytics.lake.state.TranslatorState.OpenInstance;
import io.camunda.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * ZeebeDb value flyweight for an {@link OpenInstance}. UTF-8 string bytes are cached on {@link
 * #set} so {@link #getLength()} and {@link #write} — called in that order by ZeebeDb — agree on the
 * serialized size without re-encoding.
 */
final class OpenInstanceValue implements DbValue {

  private static final byte[] EMPTY = new byte[0];

  private long processDefinitionKey;
  private int version;
  private long startMs;
  private byte[] processId = EMPTY;
  private byte[] tenantId = EMPTY;

  OpenInstanceValue set(final OpenInstance instance) {
    processDefinitionKey = instance.processDefinitionKey();
    version = instance.version();
    startMs = instance.startMs();
    processId = instance.processId().getBytes(UTF_8);
    tenantId = instance.tenantId().getBytes(UTF_8);
    return this;
  }

  OpenInstance toRecord() {
    return new OpenInstance(
        processDefinitionKey,
        new String(processId, UTF_8),
        version,
        new String(tenantId, UTF_8),
        startMs);
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    processDefinitionKey = buffer.getLong(offset);
    offset += Long.BYTES;
    version = buffer.getInt(offset);
    offset += Integer.BYTES;
    startMs = buffer.getLong(offset);
    offset += Long.BYTES;
    processId = LakeValueCodec.getBytes(buffer, offset);
    offset += LakeValueCodec.sizeOf(processId);
    tenantId = LakeValueCodec.getBytes(buffer, offset);
  }

  @Override
  public int getLength() {
    return Long.BYTES
        + Integer.BYTES
        + Long.BYTES
        + LakeValueCodec.sizeOf(processId)
        + LakeValueCodec.sizeOf(tenantId);
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, processDefinitionKey);
    offset += Long.BYTES;
    buffer.putInt(offset, version);
    offset += Integer.BYTES;
    buffer.putLong(offset, startMs);
    offset += Long.BYTES;
    offset = LakeValueCodec.putBytes(buffer, offset, processId);
    LakeValueCodec.putBytes(buffer, offset, tenantId);
    return getLength();
  }
}

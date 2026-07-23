/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.camunda.analytics.lake.state.TranslatorState.VariantElementKind;
import io.camunda.analytics.lake.state.TranslatorState.VariantName;
import io.camunda.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * ZeebeDb value flyweight for a {@link VariantName}: the length-prefixed UTF-8 id, followed by one
 * kind tag byte ({@code 0} = {@link VariantElementKind#ELEMENT}, {@code 1} = {@link
 * VariantElementKind#FLOW}).
 */
final class VariantNameValue implements DbValue {

  private static final byte[] EMPTY = new byte[0];
  private static final byte KIND_ELEMENT = 0;
  private static final byte KIND_FLOW = 1;

  private byte[] id = EMPTY;
  private byte kind;

  VariantNameValue set(final VariantName name) {
    id = name.id().getBytes(UTF_8);
    kind = name.kind() == VariantElementKind.FLOW ? KIND_FLOW : KIND_ELEMENT;
    return this;
  }

  VariantName toRecord() {
    return new VariantName(
        new String(id, UTF_8),
        kind == KIND_FLOW ? VariantElementKind.FLOW : VariantElementKind.ELEMENT);
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    id = LakeValueCodec.getBytes(buffer, offset);
    offset += LakeValueCodec.sizeOf(id);
    kind = buffer.getByte(offset);
  }

  @Override
  public int getLength() {
    return LakeValueCodec.sizeOf(id) + Byte.BYTES;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    offset = LakeValueCodec.putBytes(buffer, offset, id);
    buffer.putByte(offset, kind);
    return getLength();
  }
}

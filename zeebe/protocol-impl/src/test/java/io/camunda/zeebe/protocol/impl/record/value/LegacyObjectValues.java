/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Serialization of the frozen {@code legacy} nested values. They are plain {@link ObjectValue}s,
 * which only offer the {@link MsgPackWriter}/{@link MsgPackReader} based API.
 */
public final class LegacyObjectValues {

  private LegacyObjectValues() {}

  public static int write(final ObjectValue value, final MutableDirectBuffer buffer) {
    final var writer = new MsgPackWriter();
    writer.wrap(buffer, 0);
    value.write(writer);
    return writer.getOffset();
  }

  public static int length(final ObjectValue value) {
    return value.getEncodedLength();
  }

  public static void read(final ObjectValue value, final DirectBuffer buffer, final int length) {
    final var reader = new MsgPackReader();
    reader.wrap(buffer, 0, length);
    value.read(reader);
  }
}

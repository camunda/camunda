/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Converts a record into its frozen XxxRecordV1 copy (or back) by round-tripping the serialized
 * bytes, so that the frozen state classes only ever hold V1 classes. Test only.
 */
public final class RecordV1s {

  private RecordV1s() {}

  /** Copies the bytes of {@code source} into {@code target} and returns {@code target}. */
  public static <T extends BufferReader> T convert(final BufferWriter source, final T target) {
    final var buffer = new UnsafeBuffer(new byte[source.getLength()]);
    source.write(buffer, 0);
    target.wrap(buffer, 0, buffer.capacity());
    return target;
  }
}

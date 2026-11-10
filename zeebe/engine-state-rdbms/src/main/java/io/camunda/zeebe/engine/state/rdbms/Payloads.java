/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.util.buffer.BufferReader;
import org.agrona.concurrent.UnsafeBuffer;

/** Converts engine state values to and from the serialized msgpack payloads of their rows. */
final class Payloads {

  private Payloads() {}

  static byte[] serialize(final DbValue value) {
    final var payload = new byte[value.getLength()];
    value.write(new UnsafeBuffer(payload), 0);
    return payload;
  }

  /** Reads the payload into the target, which may keep viewing it: never let it see a row's. */
  static <T extends BufferReader> T wrapCopy(final T target, final byte[] payload) {
    final var copy = payload.clone();
    target.wrap(new UnsafeBuffer(copy), 0, copy.length);
    return target;
  }
}

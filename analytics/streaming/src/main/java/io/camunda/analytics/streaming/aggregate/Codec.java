/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

/**
 * Converts a value to and from bytes so it can be held in the durable state store. The library is
 * generic over the grouping key and accumulator types, so it cannot know how to serialize them —
 * the domain supplies a codec for each. Implementations must be deterministic and round-trip
 * exactly: {@code decode(encode(v)).equals(v)}.
 *
 * @param <T> the value type
 */
public interface Codec<T> {

  byte[] encode(T value);

  T decode(byte[] bytes);
}

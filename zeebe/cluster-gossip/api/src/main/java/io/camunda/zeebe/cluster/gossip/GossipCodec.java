/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip;

/**
 * Encodes values to bytes and back.
 *
 * <p>Implementations must be deterministic: equal values must encode to identical bytes, because
 * the gossiper detects changes by comparing encoded bytes. Encoding a {@link java.util.HashMap} by
 * iterating over it, for example, is not deterministic.
 *
 * <p>Evolving the encoded format across versions, for example during a rolling update, is the
 * responsibility of the codec.
 *
 * @param <T> the type of the value
 */
public interface GossipCodec<T> {

  byte[] encode(T value);

  /**
   * @throws RuntimeException if the bytes cannot be decoded; the value then looks absent to local
   *     readers
   */
  T decode(byte[] bytes);
}

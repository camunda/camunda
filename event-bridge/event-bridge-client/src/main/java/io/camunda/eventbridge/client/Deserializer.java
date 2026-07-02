/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

/**
 * Converts a raw record payload into a typed value for the handler-based consumer (see {@link
 * EventBridgeClient#consume()}). The partition and offset are provided so a deserializer can carry
 * positional metadata into the produced value if needed.
 *
 * @param <T> the type produced from the payload
 */
@FunctionalInterface
public interface Deserializer<T> {

  /**
   * Deserializes {@code payload}, which was read at {@code offset} of {@code partition}.
   *
   * @param payload the raw record bytes
   * @param partition the partition the record was read from
   * @param offset the log position of the record
   * @return the deserialized value
   */
  T deserialize(byte[] payload, int partition, long offset);
}

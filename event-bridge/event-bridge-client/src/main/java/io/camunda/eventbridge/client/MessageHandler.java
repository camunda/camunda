/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

/**
 * Callback invoked by a {@link MessageConsumer} for each record delivered by the managed poll loop.
 *
 * @param <T> the deserialized record type (raw {@link Event} when no {@link Deserializer} is set)
 */
@FunctionalInterface
public interface MessageHandler<T> {

  /**
   * Handles a single record. Invoked on the consumer's background poll thread, so a long-running
   * handler holds up the loop for its partition batch. Exceptions thrown here are logged and the
   * loop continues.
   *
   * @param record the (deserialized) record
   */
  void handle(T record);
}

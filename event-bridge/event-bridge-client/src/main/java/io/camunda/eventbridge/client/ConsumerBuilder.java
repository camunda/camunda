/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import java.time.Duration;
import java.util.List;

/**
 * Fluent builder for a managed, handler-based consumer, obtained via {@link
 * EventBridgeClient#consume()}. It hides the poll loop: register a {@link MessageHandler} and
 * {@link #start()} a background daemon thread that polls, dispatches, and (by default)
 * auto-commits.
 *
 * <p>Records are delivered as raw {@link Event}s unless a {@link Deserializer} is supplied via
 * {@link #deserializer(Deserializer)}, in which case each record's payload is deserialized to
 * {@code T} before dispatch.
 *
 * <pre>{@code
 * var handle = client.consume()
 *     .topics("orders")
 *     .group("billing")
 *     .handler(event -> process(event))
 *     .autoCommit(true)
 *     .start();
 * // ... later
 * handle.close();
 * }</pre>
 *
 * @param <T> the record type delivered to the handler
 */
public interface ConsumerBuilder<T> {

  /** Sets the topics to subscribe to. */
  ConsumerBuilder<T> topics(String... topics);

  /** Sets the topics to subscribe to. */
  ConsumerBuilder<T> topics(List<String> topics);

  /** Sets the consumer group id (required). */
  ConsumerBuilder<T> group(String group);

  /** Sets the consumer instance id within the group. Defaults to a random id when unset. */
  ConsumerBuilder<T> instanceId(String instanceId);

  /**
   * Sets a deserializer applied to each record's payload before dispatch, switching the delivered
   * type to {@code R}.
   */
  <R> ConsumerBuilder<R> deserializer(Deserializer<R> deserializer);

  /** Registers the handler invoked for every delivered record (required). */
  ConsumerBuilder<T> handler(MessageHandler<T> handler);

  /** Enables or disables per-batch auto-commit of the max processed offset. Defaults to true. */
  ConsumerBuilder<T> autoCommit(boolean autoCommit);

  /** Sets the maximum number of records fetched per poll. Defaults to 100. */
  ConsumerBuilder<T> pollSize(int pollSize);

  /** Sets how long each poll waits for records. Defaults to one second. */
  ConsumerBuilder<T> pollTimeout(Duration pollTimeout);

  /** Subscribes to the configured topics and starts the background dispatch loop. */
  MessageConsumer start();
}

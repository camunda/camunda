/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.ConsumerBuilder;
import io.camunda.eventbridge.client.Deserializer;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.MessageConsumer;
import io.camunda.eventbridge.client.MessageHandler;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Default {@link ConsumerBuilder} implementation. Subscribes an underlying {@link Consumer} through
 * the client and wraps it in a {@link ManagedConsumer} that runs the poll/dispatch/commit loop.
 *
 * <p>The generic type shifts when a {@link #deserializer(Deserializer)} is set: the same mutable
 * builder is reused and cast, since the runtime state (handler, deserializer) is type-erased.
 *
 * @param <T> the record type delivered to the handler
 */
public final class ConsumerBuilderImpl<T> implements ConsumerBuilder<T> {

  private final EventBridgeClient client;
  private final ExecutorService executor;

  private List<String> topics = List.of();
  private String group;
  private String instanceId = "consumer-" + UUID.randomUUID();
  private Deserializer<T> deserializer;
  private MessageHandler<T> handler;
  private boolean autoCommit = true;
  private int pollSize = 100;
  private Duration pollTimeout = Duration.ofSeconds(1);

  public ConsumerBuilderImpl(final EventBridgeClient client, final ExecutorService executor) {
    this.client = client;
    this.executor = executor;
  }

  @Override
  public ConsumerBuilder<T> topics(final String... topics) {
    this.topics = List.of(topics);
    return this;
  }

  @Override
  public ConsumerBuilder<T> topics(final List<String> topics) {
    this.topics = List.copyOf(topics);
    return this;
  }

  @Override
  public ConsumerBuilder<T> group(final String group) {
    this.group = group;
    return this;
  }

  @Override
  public ConsumerBuilder<T> instanceId(final String instanceId) {
    this.instanceId = instanceId;
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> ConsumerBuilder<R> deserializer(final Deserializer<R> deserializer) {
    final ConsumerBuilderImpl<R> self = (ConsumerBuilderImpl<R>) this;
    self.deserializer = deserializer;
    return self;
  }

  @Override
  public ConsumerBuilder<T> handler(final MessageHandler<T> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public ConsumerBuilder<T> autoCommit(final boolean autoCommit) {
    this.autoCommit = autoCommit;
    return this;
  }

  @Override
  public ConsumerBuilder<T> pollSize(final int pollSize) {
    this.pollSize = pollSize;
    return this;
  }

  @Override
  public ConsumerBuilder<T> pollTimeout(final Duration pollTimeout) {
    this.pollTimeout = pollTimeout;
    return this;
  }

  @Override
  public MessageConsumer start() {
    Objects.requireNonNull(group, "group is required");
    Objects.requireNonNull(handler, "handler is required");
    final Consumer consumer = client.subscribe(group, instanceId, topics).join();
    return new ManagedConsumer<>(
        consumer, executor, handler, deserializer, autoCommit, pollSize, pollTimeout);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** A mock implementation of ClusterCommunicationService */
final class TestCommunicationService implements ClusterCommunicationService {

  private final ConcurrentMap<String, BiFunction<MemberId, byte[], CompletableFuture<byte[]>>>
      subscribers = new ConcurrentHashMap<>();
  private final Map<MemberId, TestCommunicationService> cluster;

  private final MemberId memberId;

  TestCommunicationService(
      final ConcurrentMap<MemberId, TestCommunicationService> cluster, final MemberId memberId) {
    this.cluster = cluster;
    this.memberId = memberId;
    cluster.put(memberId, this);
  }

  @Override
  public <M> void broadcast(
      final String subject,
      final M message,
      final Function<M, byte[]> encoder,
      final boolean reliable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <M> void multicast(
      final String subject,
      final M message,
      final Function<M, byte[]> encoder,
      final Set<MemberId> memberIds,
      final boolean reliable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <M> void unicast(
      final String subject,
      final M message,
      final Function<M, byte[]> encoder,
      final MemberId memberId,
      final boolean reliable) {
    // Do nothing
  }

  @Override
  public <M, R> CompletableFuture<R> send(
      final String subject,
      final M message,
      final Function<M, byte[]> encoder,
      final Function<byte[], R> decoder,
      final MemberId toMemberId,
      final Duration timeout) {

    final var subscriber =
        cluster
            .get(toMemberId)
            .subscribers
            .getOrDefault(
                subject,
                (m, r) -> {
                  throw new RuntimeException("No subscriber found");
                });
    return subscriber.apply(memberId, encoder.apply(message)).thenApply(decoder);
  }

  @Override
  public <M, R> void subscribe(
      final String subject,
      final Function<byte[], M> decoder,
      final Function<M, R> handler,
      final Function<R, byte[]> encoder,
      final Executor executor) {

    subscribers.put(
        subject,
        (member, request) -> {
          final M decodedRequest = decoder.apply(request);
          final CompletableFuture<byte[]> encodedResult = new CompletableFuture<>();
          executor.execute(
              () -> {
                final var result = handler.apply(decodedRequest);
                encodedResult.complete(encoder.apply(result));
              });
          return encodedResult;
        });
  }

  @Override
  public <M, R> void subscribe(
      final String subject,
      final Function<byte[], M> decoder,
      final Function<M, CompletableFuture<R>> handler,
      final Function<R, byte[]> encoder) {
    subscribers.put(
        subject,
        (member, request) -> {
          final M decodedRequest = decoder.apply(request);
          final CompletableFuture<byte[]> encodedResult = new CompletableFuture<>();
          final var resultFuture = handler.apply(decodedRequest);
          resultFuture.whenComplete(
              (result, error) -> {
                if (error != null) {
                  encodedResult.completeExceptionally(error);
                } else {
                  encodedResult.complete(encoder.apply(result));
                }
              });
          return encodedResult;
        });
  }

  @Override
  public <M> void subscribe(
      final String subject,
      final Function<byte[], M> decoder,
      final Consumer<M> handler,
      final Executor executor) {
    subscribers.put(
        subject,
        (member, request) -> {
          final M decodedRequest = decoder.apply(request);
          final CompletableFuture<byte[]> encodedResult = new CompletableFuture<>();
          executor.execute(
              () -> {
                handler.accept(decodedRequest);
                encodedResult.complete(null);
              });
          return encodedResult;
        });
  }

  @Override
  public <M> void subscribe(
      final String subject,
      final Function<byte[], M> decoder,
      final BiConsumer<MemberId, M> handler,
      final Executor executor) {
    subscribers.put(
        subject,
        (member, request) -> {
          final M decodedRequest = decoder.apply(request);
          final CompletableFuture<byte[]> encodedResult = new CompletableFuture<>();
          executor.execute(
              () -> {
                handler.accept(member, decodedRequest);
                encodedResult.complete(null);
              });
          return encodedResult;
        });
  }

  @Override
  public void unsubscribe(final String subject) {
    subscribers.remove(subject);
  }
}

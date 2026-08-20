/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3.util;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Phaser;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregates the results of futures published by a {@link
 * org.reactivestreams.Publisher<CompletableFuture>}.
 *
 * <p>Once the subscription is started, multiple futures are requested from the publisher. New
 * futures are requested whenever a future completes. The maximum number of requested futures must
 * be provided in {@link AsyncAggregatingSubscriber#AsyncAggregatingSubscriber(long parallelism)}}
 *
 * <p>If a futures completes exceptionally, the result is completed exceptionally.
 */
@SuppressWarnings("ReactiveStreamsSubscriberImplementation")
public class AsyncAggregatingSubscriber<T> implements Subscriber<CompletableFuture<T>> {
  private static final Logger LOG = LoggerFactory.getLogger(AsyncAggregatingSubscriber.class);

  final ConcurrentLinkedDeque<T> results = new ConcurrentLinkedDeque<>();
  final CompletableFuture<Collection<T>> resultsFuture = new CompletableFuture<>();

  // Phaser used to await for all results. New parties are registered for every new future in
  // onNext().
  final Phaser phaser = new Phaser(1); // arrives in result()
  private Subscription subscription;
  private final long parallelism;

  public AsyncAggregatingSubscriber(final long parallelism) {
    this.parallelism = parallelism;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    LOG.trace("Subscription started");
    phaser.register(); // arrives in onComplete()
    this.subscription = subscription;
    subscription.request(parallelism);
  }

  @Override
  public void onNext(final CompletableFuture<T> future) {
    LOG.trace("Received next future: {}", future);
    phaser.register(); // arrives in handleAsync
    future.handleAsync(
        (result, throwable) -> {
          if (throwable == null) {
            LOG.trace("Completed: {}", result);
            results.add(result);
            if (phaser.arrive() >= 0) {
              subscription.request(1);
            }
          } else {
            LOG.trace("Future failed.", throwable);
            resultsFuture.completeExceptionally(throwable);
            phaser.forceTermination();
            subscription.cancel();
          }
          return null;
        });
  }

  @Override
  public void onError(final Throwable t) {
    LOG.trace("Subscription failed.", t);
    resultsFuture.completeExceptionally(t);
    phaser.forceTermination();
  }

  @Override
  public void onComplete() {
    LOG.trace("Completed subscription");
    phaser.arrive();
  }

  /**
   * @return A future that is completed with the results once all of them have been collected.
   */
  public CompletableFuture<Collection<T>> result() {
    return CompletableFuture.supplyAsync(phaser::arriveAndAwaitAdvance)
        .thenCompose(
            ignored -> {
              resultsFuture.complete(results);
              return resultsFuture;
            });
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.concurrency;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class FuturesUtil {

  /**
   * Traverse the provided collection sequentially, applying the provided function making sure that
   * the futures are started sequentially: When one computation completes, the next starts after it.
   *
   * @param collection to traverse to use as input for the function
   * @param function the computation to execute for each element of the collection
   * @return a Void future that will complete successfully if all Futures started complete
   *     successfully. if one future returns an error, the computation is stopped (the rest of the
   *     Future will not be started) and the error is returned
   */
  public static <A> CompletableFuture<Void> traverseIgnoring(
      final Collection<A> collection,
      final Function<A, CompletableFuture<Void>> function,
      final Executor executor) {
    var future = CompletableFuture.<Void>completedFuture(null);
    for (final A a : collection) {
      future = future.thenComposeAsync(unused -> function.apply(a), executor);
    }
    return future;
  }

  /**
   * Traverse the collection and starts all futures immediately without waiting for completion, then
   * wait for all tasks to be completed and then return a List containing all results. If any error
   * is encountered, the returned future fails, but it will spawn one CompletableFuture for each
   * element in the collection.
   *
   * @param collection the collection containing the element to traverse
   * @param function the task that will be spawned
   * @return a CompletableFuture with all the results combined
   */
  @SuppressWarnings("unchecked")
  public static <A, B> CompletableFuture<List<B>> parTraverse(
      final Collection<A> collection, final Function<A, CompletableFuture<B>> function) {
    final CompletableFuture<B>[] futures =
        collection.stream().map(function).toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(futures)
        .thenApply(unused -> Arrays.stream(futures).map(CompletableFuture::join).toList());
  }
}

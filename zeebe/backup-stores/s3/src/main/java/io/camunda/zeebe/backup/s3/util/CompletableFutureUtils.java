/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3.util;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CompletableFutureUtils {

  // Hide default constructor
  private CompletableFutureUtils() {}

  /**
   * Maps keys to values by applying a mapping function asynchronously.
   *
   * @param keys A collection of keys.
   * @param valueMapper A function that returns a {@link CompletableFuture<V>} for every key.
   * @return A future that completes with a map of keys to the completed result of applying the
   *     mapping function. If the mapping function completes exceptionally, the resulting future
   *     will also complete exceptionally. @ param <K> Type of keys
   * @param <V> Type of value
   */
  public static <K, V> CompletableFuture<Map<K, V>> mapAsync(
      final Collection<K> keys, final Function<K, CompletableFuture<V>> valueMapper) {
    return mapAsync(keys, Function.identity(), valueMapper);
  }

  /**
   * Maps keys to values by applying a mapping function asynchronously.
   *
   * @param coll A collection where elements will be mapped to keys and value by the provided
   *     functions.
   * @param valueMapper A function that returns a {@link CompletableFuture<V>} for every key.
   * @return A future that completes with a map of keys to the completed result of applying the
   *     mapping function. If the mapping function completes exceptionally, the resulting future
   *     will also complete exceptionally. @ param <K> Type of keys
   * @param <V> Type of value
   */
  public static <C, K, V> CompletableFuture<Map<K, V>> mapAsync(
      final Collection<C> coll,
      final Function<C, K> keyMapper,
      final Function<C, CompletableFuture<V>> valueMapper) {
    final var inProgress = coll.stream().collect(Collectors.toMap(keyMapper, valueMapper));
    return CompletableFuture.allOf(inProgress.values().toArray(CompletableFuture[]::new))
        .thenApply(
            unused ->
                inProgress.entrySet().stream()
                    .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().join())));
  }
}

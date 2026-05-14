/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import com.google.common.base.Strings;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrdinalIndexManager {
  public static final String ORDINAL_SUFFIX_PREFIX = "ord";
  private static final Logger LOGGER = LoggerFactory.getLogger(OrdinalIndexManager.class);
  private final SearchEngineClient searchEngineClient;
  private final Set<String> ordinalBasedIndexes;
  private final Map<Integer, Ordinal> ordinals = new ConcurrentHashMap<>();

  public OrdinalIndexManager(
      final SearchEngineClient searchEngineClient, final Set<String> ordinalBasedIndexes) {
    this.searchEngineClient = searchEngineClient;
    this.ordinalBasedIndexes = ordinalBasedIndexes;
  }

  public boolean isOrdinalBasedIndex(final String index) {
    return ordinalBasedIndexes.contains(index);
  }

  public String getOrCreateOrdinalSuffix(final int ordinal) {
    final var ord = ensureOrdinalIndexesExist(ordinal);
    ord.markActive();
    return ord.suffix;
  }

  private Ordinal ensureOrdinalIndexesExist(final int ordinal) {
    var ord = ordinals.get(ordinal);
    if (ord == null) {
      final var suffix = createOrdinalSuffix(ordinal);
      runAgainstAllOrdinalIndexes(
          suffix,
          index -> {
            LOGGER.info("Create ordinal index '{}'", index);
            searchEngineClient.createOrdinalIndex(index);
          });
      final var newOrd = new Ordinal(suffix);
      ord = ordinals.putIfAbsent(ordinal, newOrd);
      if (ord == null) {
        LOGGER.info(
            "New ordinal indexes created: {} ({} ordinals total)", ordinal, ordinals.size());
        ord = newOrd;
      }
    }
    return ord;
  }

  private String createOrdinalSuffix(final int ordinal) {
    return ORDINAL_SUFFIX_PREFIX + Strings.padStart(String.valueOf(ordinal), 5, '0');
  }

  public void runIndexManagement() {
    ensureNextReady();
    updateLifecycles();
  }

  public void ensureNextReady() {
    int maxOrdinal = -1;
    for (final var entry : ordinals.entrySet()) {
      maxOrdinal = Math.max(entry.getKey(), maxOrdinal);
      if (entry.getValue().getState() == Ordinal.State.PENDING) {
        return;
      }
    }
    final int nextOrdinal = maxOrdinal + 1;
    ensureOrdinalIndexesExist(nextOrdinal);
  }

  public void updateLifecycles() {
    final var activeOrdinals =
        ordinals.entrySet().stream()
            .filter(entry -> entry.getValue().getState() == Ordinal.State.ACTIVE)
            .sorted(Map.Entry.comparingByKey())
            .toList();

    // bit of fudge, but we'll allow two "active" ordinals
    // as during max benchmark we'd usually expect the oldest ordinal
    // to be fully finished and ok to set ILM on
    if (activeOrdinals.size() > 2) {
      final var entry = activeOrdinals.getFirst();
      final var ordinal = entry.getKey();
      LOGGER.info("Marking ordinal {} as delete pending", ordinal);
      setLifeCycleDeletionForOrdinal(ordinal);
      entry.getValue().markDeletePending();
    }
  }

  private void setLifeCycleDeletionForOrdinal(final int ordinal) {
    final var suffix = createOrdinalSuffix(ordinal);
    runAgainstAllOrdinalIndexes(
        suffix,
        index -> {
          LOGGER.info("Set lifecycle deletion for ordinal index '{}'", index);
          searchEngineClient.setOrdinalIndexLifeCyclePolicy(index);
        });
  }

  private void runAgainstAllOrdinalIndexes(
      final String suffix, final Consumer<String> indexConsumer) {
    try (final var virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      final var futures =
          indexesForOrdinal(suffix)
              .map(
                  index ->
                      // run update of indices async as virtual thread
                      CompletableFuture.runAsync(
                          () -> {
                            indexConsumer.accept(index);
                          },
                          virtualThreadExecutor))
              .toArray(CompletableFuture[]::new);

      CompletableFuture.allOf(futures).join();
    }
  }

  private Stream<String> indexesForOrdinal(final String suffix) {
    return ordinalBasedIndexes.stream().map(index -> index + suffix);
  }

  static class Ordinal {
    private final String suffix;
    private final AtomicReference<State> state;

    public Ordinal(final String suffix) {
      this.suffix = suffix;
      state = new AtomicReference<>(State.PENDING);
    }

    public State getState() {
      return state.get();
    }

    public void markActive() {
      state.set(State.ACTIVE);
    }

    public void markDeletePending() {
      state.set(State.DELETE_PENDING);
    }

    enum State {
      PENDING,
      ACTIVE,
      DELETE_PENDING;
    }
  }
}

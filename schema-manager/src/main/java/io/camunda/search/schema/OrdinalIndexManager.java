/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import com.google.common.base.Strings;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
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
      initialiseOrdinalIndices(ordinalBasedIndexes, suffix);
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

  private void initialiseOrdinalIndices(
      final Collection<String> indices, final String ordinalSuffix) {
    try (final var virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      final var futures =
          indices.stream()
              .map(
                  index ->
                      // run creation of indices async as virtual thread
                      CompletableFuture.runAsync(
                          () -> {
                            final var fullName = index + ordinalSuffix;
                            LOGGER.info("Create ordinal index '{}'", fullName);
                            searchEngineClient.createOrdinalIndex(fullName);
                          },
                          virtualThreadExecutor))
              .toArray(CompletableFuture[]::new);

      CompletableFuture.allOf(futures).join();
    }
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

    enum State {
      PENDING,
      ACTIVE;
    }
  }
}

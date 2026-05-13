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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrdinalIndexManager {
  public static final String ORDINAL_SUFFIX_PREFIX = "ord";
  private static final Logger LOGGER = LoggerFactory.getLogger(OrdinalIndexManager.class);
  private final SearchEngineClient searchEngineClient;
  private final Set<String> ordinalBasedIndexes;
  private final Map<Integer, String> ordinalSuffixes = new ConcurrentHashMap<>();

  public OrdinalIndexManager(
      final SearchEngineClient searchEngineClient, final Set<String> ordinalBasedIndexes) {
    this.searchEngineClient = searchEngineClient;
    this.ordinalBasedIndexes = ordinalBasedIndexes;
  }

  public boolean isOrdinalBasedIndex(final String index) {
    return ordinalBasedIndexes.contains(index);
  }

  public String getOrCreateOrdinalSuffix(final int ordinal) {
    var suffix = ordinalSuffixes.get(ordinal);
    if (suffix == null) {
      final var newSuffix = createOrdinalSuffix(ordinal);
      suffix = ordinalSuffixes.putIfAbsent(ordinal, newSuffix);
      if (suffix == null) {
        LOGGER.info("New ordinal started: {} ({} ordinals total)", ordinal, ordinalSuffixes.size());
        suffix = newSuffix;
        // TODO async plus more atomic
        initialiseOrdinalIndices(ordinalBasedIndexes, suffix);
      }
    }
    return suffix;
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
}

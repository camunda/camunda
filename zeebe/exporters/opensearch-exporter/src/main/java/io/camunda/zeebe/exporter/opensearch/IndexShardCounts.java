/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.opensearch.RecordIndexRouter.ShardCountResolver;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.indices.IndexState;

/**
 * Reads how many primary shards an index has, so that {@link RecordIndexRouter} can aim a routing
 * value at one of them.
 *
 * <p>The number is read from the index rather than taken from the configuration, because an index
 * keeps the number of shards it was created with. Deriving routing values from the configured
 * number would change all of them the moment an operator changes the setting, and re-exporting a
 * record into an index created under the old number would then place a second copy of it on another
 * shard instead of overwriting the first.
 */
final class IndexShardCounts implements ShardCountResolver {

  private final OpenSearchClient client;
  private final IndexConfiguration config;
  private final Map<String, Integer> countsByIndex = new ConcurrentHashMap<>();

  IndexShardCounts(final OpenSearchClient client, final IndexConfiguration config) {
    this.client = client;
    this.config = config;
  }

  /**
   * The answer is cached for as long as the exporter lives, which is safe because an index cannot
   * change its number of shards, and necessary because the routing values derived from it have to
   * stay the same for as long as the index is written to.
   */
  @Override
  public Integer numberOfShardsOf(final String index) {
    final Integer configuredNumberOfShards = config.getNumberOfShards();
    if (configuredNumberOfShards == null) {
      // The exporter leaves the number of shards to the search engine, so there is nothing to aim
      // at. Returning early also spares a request per index.
      return null;
    }

    return countsByIndex.computeIfAbsent(
        index, name -> readNumberOfShards(name, configuredNumberOfShards));
  }

  /**
   * An index that does not exist yet is about to be created from this exporter's template and will
   * have the configured number of shards, so that is the answer for it. A search engine that cannot
   * be reached raises instead of being guessed for: a routing value derived from the wrong number
   * sends a re-exported record to a shard other than the one already holding it.
   */
  private Integer readNumberOfShards(final String index, final Integer configuredNumberOfShards) {
    final IndexState state;
    try {
      state =
          client
              .indices()
              .getSettings(settings -> settings.index(index).ignoreUnavailable(true))
              .result()
              .get(index);
    } catch (final IOException | OpenSearchException e) {
      throw new OpensearchExporterException(
          "Failed to read the number of shards of index " + index, e);
    }

    if (state == null || state.settings() == null) {
      return configuredNumberOfShards;
    }

    final var settings = state.settings();
    final var numberOfShards =
        settings.index() != null && settings.index().numberOfShards() != null
            ? settings.index().numberOfShards()
            : settings.numberOfShards();

    return numberOfShards == null ? configuredNumberOfShards : Integer.valueOf(numberOfShards);
  }
}

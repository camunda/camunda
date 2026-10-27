/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.client.sync;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.opensearch.client.opensearch.indices.RefreshResponse;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.slf4j.Logger;

public class OpenSearchIndexOperations extends OpenSearchRetryOperation {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OpenSearchIndexOperations.class);

  public OpenSearchIndexOperations(
      final OpenSearchClient openSearchClient, final OptimizeIndexNameService indexNameService) {
    super(openSearchClient, indexNameService);
  }

  private static String defaultIndexErrorMessage(final String index) {
    return String.format("Failed to search index: %s", index);
  }

  public Set<String> getIndexNamesWithRetries(final String namePattern) {
    final String prefixedNamePattern = applyIndexPrefix(namePattern);
    return executeWithRetries(
        "Get indices for " + prefixedNamePattern,
        () -> {
          try {
            final GetIndexResponse response =
                openSearchClient.indices().get(i -> i.index(prefixedNamePattern));
            return response.result().keySet();
          } catch (final OpenSearchException e) {
            if (e.status() == 404) {
              return Set.of();
            }
            throw e;
          }
        });
  }

  public boolean createIndex(final CreateIndexRequest createIndexRequest) throws IOException {
    return openSearchClient.indices().create(createIndexRequest).acknowledged();
  }

  public boolean indicesExist(final List<String> unprefixedIndexes) throws IOException {
    final List<String> indexes = applyIndexPrefix(unprefixedIndexes.toArray(new String[0]));
    final ExistsRequest.Builder existsRequest = new ExistsRequest.Builder().index(indexes);
    return openSearchClient.indices().exists(existsRequest.build()).value();
  }

  public boolean indexExists(final String unprefixedIndex) {
    final String index = applyIndexPrefix(unprefixedIndex);
    return safe(
        // allowNoIndices must be set to false, otherwise index names containing wildcards will
        // always return true
        () ->
            openSearchClient
                .indices()
                .exists(r -> r.index(getIndexAliasFor(index)).allowNoIndices(false))
                .value(),
        e -> defaultIndexErrorMessage(index));
  }

  public void refresh(final String unprefixedIndexPattern) {
    final String indexPattern = applyIndexPrefix(unprefixedIndexPattern);
    final RefreshRequest refreshRequest = new RefreshRequest.Builder().index(indexPattern).build();
    try {
      final RefreshResponse refresh = openSearchClient.indices().refresh(refreshRequest);
      if (!refresh.shards().failures().isEmpty()) {
        LOG.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (final Exception ex) {
      LOG.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }

  public void refresh(final String... unprefixedIndexPatterns) {
    final List<String> indexPatterns = applyIndexPrefix(unprefixedIndexPatterns);
    final RefreshRequest refreshRequest = new RefreshRequest.Builder().index(indexPatterns).build();
    try {
      final RefreshResponse refresh = openSearchClient.indices().refresh(refreshRequest);
      if (!refresh.shards().failures().isEmpty()) {
        LOG.warn("Unable to refresh indices: {}", List.of(indexPatterns));
      }
    } catch (final Exception ex) {
      LOG.warn(String.format("Unable to refresh indices: %s", List.of(indexPatterns)), ex);
    }
  }

  private Set<String> getFilteredIndices(final String indexPattern) throws IOException {
    return openSearchClient.indices().get(i -> i.index(List.of(indexPattern))).result().keySet();
  }

  public boolean deleteIndicesWithRetries(final String... unprefixedIndexPatterns) {
    for (final String unprefixedIndexPattern : unprefixedIndexPatterns) {
      final String indexPattern = applyIndexPrefix(unprefixedIndexPattern);
      final boolean success =
          executeWithRetries(
              "DeleteIndices " + indexPattern,
              () -> {
                for (final String index : getFilteredIndices(indexPattern)) {
                  openSearchClient.indices().delete(d -> d.index(List.of(index)));
                }
                return true;
              });
      if (!success) {
        return false;
      }
    }
    return true;
  }

  public PutIndicesSettingsResponse putSettings(final PutIndicesSettingsRequest request)
      throws IOException {
    return openSearchClient.indices().putSettings(request);
  }

  // Returns if task is completed under these conditions:
  // - If the response is empty we can immediately return false to force a new reindex in outer
  // retry loop
  // - If the response has a status with uncompleted flag and a sum of changed documents
  // (created,updated and deleted documents) not equal to total documents
  //   we need to wait and poll again the task status
  private boolean waitUntilTaskIsCompleted(final String taskId, final long srcCount) {
    final GetTasksResponse taskResponse = waitTaskCompletion(taskId);

    if (taskResponse != null) {
      logProgress(taskId, taskResponse.response().total(), srcCount);

      final long total = taskResponse.response().total();
      LOG.info("Source docs: {}, Migrated docs: {}", srcCount, total);
      return total == srcCount;
    } else {
      // need to reindex again
      return false;
    }
  }

  private void logProgress(final String taskId, final long processed, final long srcCount) {
    final double progress = processed * 100.00 / srcCount;
    LOG.info("TaskId: {}, Progress: {}%", taskId, String.format("%.2f", progress));
  }

  public GetIndexResponse get(final GetIndexRequest.Builder requestBuilder) {
    final GetIndexRequest request = applyIndexPrefix(requestBuilder).build();
    return safe(
        () -> openSearchClient.indices().get(request),
        e -> "Failed to get index " + request.index());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.externalcode.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.indices.AnalyzeRequest;
import org.opensearch.client.opensearch.indices.AnalyzeResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.opensearch.client.opensearch.indices.RefreshResponse;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@Slf4j
public class OpenSearchIndexOperations extends OpenSearchRetryOperation {
  public static final String NUMBERS_OF_REPLICA = "index.number_of_replicas";
  public static final String NO_REPLICA = "0";
  public static final String REFRESH_INTERVAL = "index.refresh_interval";
  public static final String NO_REFRESH = "-1";

  public OpenSearchIndexOperations(OpenSearchClient openSearchClient,
                                   final OptimizeIndexNameService indexNameService) {
    super(openSearchClient, indexNameService);
  }

  private static String defaultIndexErrorMessage(String index) {
    return String.format("Failed to search index: %s", index);
  }

  public Set<String> getIndexNamesWithRetries(String namePattern) {
    return executeWithRetries(
      "Get indices for " + namePattern,
      () -> {
        try {
          final GetIndexResponse response = openSearchClient.indices().get(i -> i.index(namePattern));
          return response.result().keySet();
        } catch (OpenSearchException e) {
          if (e.status() == 404) {
            return Set.of();
          }
          throw e;
        }
      });
  }

  public boolean createIndexWithRetries(CreateIndexRequest createIndexRequest) {
    return executeWithRetries(
      "CreateIndex " + createIndexRequest.index(),
      () -> {
        if (!indicesExist(createIndexRequest.index())) {
          return openSearchClient.indices().create(createIndexRequest).acknowledged();
        }
        return true;
      });
  }

  private boolean indicesExist(final String indexPattern) throws IOException {
    return openSearchClient
      .indices()
      .exists(e -> e.index(List.of(indexPattern)).ignoreUnavailable(true).allowNoIndices(false))
      .value();
  }

  public long getNumberOfDocumentsWithRetries(String... indexPatterns) {
    return executeWithRetries(
      "Count number of documents in " + Arrays.asList(indexPatterns),
      () -> openSearchClient.count(c -> c.index(List.of(indexPatterns))).count());
  }

  public boolean indexExists(String index) {
    return safe(() -> openSearchClient.indices().exists(r -> r.index(getIndexAliasFor(index))).value(),
                e -> defaultIndexErrorMessage(index));
  }

  public void refresh(String indexPattern) {
    final var refreshRequest = new RefreshRequest.Builder().index(List.of(indexPattern)).build();
    try {
      final RefreshResponse refresh = openSearchClient.indices().refresh(refreshRequest);
      if (refresh.shards().failures().size() > 0) {
        log.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (Exception ex) {
      log.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }

  public void refresh(String ... indexPatterns) {
    final var refreshRequest = new RefreshRequest.Builder().index(List.of(indexPatterns)).build();
    try {
      final RefreshResponse refresh = openSearchClient.indices().refresh(refreshRequest);
      if (refresh.shards().failures().size() > 0) {
        log.warn("Unable to refresh indices: {}", List.of(indexPatterns));
      }
    } catch (Exception ex) {
      log.warn(String.format("Unable to refresh indices: %s", List.of(indexPatterns)), ex);
    }
  }

  public void refreshWithRetries(final String indexPattern) {
    executeWithRetries(
      "Refresh " + indexPattern,
      () -> {
        try {
          for (var index : getFilteredIndices(indexPattern)) {
            openSearchClient.indices().refresh(r -> r.index(List.of(index)));
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        return true;
      });
  }

  private Set<String> getFilteredIndices(final String indexPattern) throws IOException {
    return openSearchClient.indices().get(i -> i.index(List.of(indexPattern))).result().keySet();
  }

  public boolean deleteIndicesWithRetries(final String indexPattern) {
    return executeWithRetries(
      "DeleteIndices " + indexPattern,
      () -> {
        for (var index : getFilteredIndices(indexPattern)) {
          openSearchClient.indices().delete(d -> d.index(List.of(indexPattern)));
        }
        return true;
      });
  }

  public IndexSettings getIndexSettingsWithRetries(String indexName) {
    return executeWithRetries(
      "GetIndexSettings " + indexName,
      () -> {
        final GetIndicesSettingsResponse response = openSearchClient.indices().getSettings(s -> s.index(List.of(indexName)));
        return response.result().get(indexName).settings();
      });
  }

  public String getOrDefaultRefreshInterval(String indexName, String defaultValue) {
    var refreshIntervalTime = getIndexSettingsWithRetries(indexName).refreshInterval();
    String refreshInterval = refreshIntervalTime==null?defaultValue:refreshIntervalTime.time();
    if (refreshInterval.trim().equals(NO_REFRESH)) {
      refreshInterval = defaultValue;
    }
    return refreshInterval;
  }

  public String getOrDefaultNumbersOfReplica(String indexName, String defaultValue) {
    String numberOfReplicasOriginal = getIndexSettingsWithRetries(indexName).numberOfReplicas();
    String numbersOfReplica = numberOfReplicasOriginal==null?defaultValue:numberOfReplicasOriginal;
    if (numbersOfReplica.trim().equals(NO_REPLICA)) {
      numbersOfReplica = defaultValue;
    }
    return numbersOfReplica;
  }

  public PutIndicesSettingsResponse putSettings(PutIndicesSettingsRequest request) throws IOException {
    return openSearchClient.indices().putSettings(request);
  }

  public PutIndicesSettingsResponse setIndexLifeCycle(String index, String value) throws IOException {
    var request = PutIndicesSettingsRequest.of(b -> b.index(index).settings(s -> s.lifecycleName(value)));
    return putSettings(request);
  }

  public boolean setIndexSettingsFor(IndexSettings settings, String indexPattern) {
    return executeWithRetries(
      "SetIndexSettings " + indexPattern,
      () ->
        openSearchClient
          .indices()
          .putSettings(s -> s.index(indexPattern).settings(settings))
          .acknowledged());
  }

  public AnalyzeResponse analyze(AnalyzeRequest analyzeRequest) throws IOException {
    return openSearchClient.indices().analyze(analyzeRequest);
  }

  // TODO check unused
  public void reindexWithRetries(final ReindexRequest reindexRequest) {
    reindexWithRetries(reindexRequest, true);
  }

  // TODO check unused
  public void reindexWithRetries(final ReindexRequest reindexRequest, boolean checkDocumentCount) {
    executeWithRetries(
      "Reindex "
        + Arrays.asList(reindexRequest.source().index())
        + " -> "
        + reindexRequest.dest().index(),
      () -> {
        final String srcIndices = reindexRequest.source().index().get(0);
        final long srcCount = getNumberOfDocumentsWithRetries(srcIndices);
        if (checkDocumentCount) {
          final String dstIndex = reindexRequest.dest().index();
          final long dstCount = getNumberOfDocumentsWithRetries(dstIndex + "*");
          if (srcCount == dstCount) {
            log.info("Reindex of {} -> {} is already done.", srcIndices, dstIndex);
            return true;
          }
        }
        var response = openSearchClient.reindex(reindexRequest);

        if(response.total().equals(srcCount)) {
          var taskId = response.task() != null ? response.task() : "task:unavailable";
          logProgress(taskId, srcCount, srcCount);
          return true;
        }

        TimeUnit.of(ChronoUnit.MILLIS).sleep(2_000);
        return waitUntilTaskIsCompleted(response.task(), srcCount);
      },
      done -> !done);
  }

  // Returns if task is completed under this conditions:
  // - If the response is empty we can immediately return false to force a new reindex in outer
  // retry loop
  // - If the response has a status with uncompleted flag and a sum of changed documents
  // (created,updated and deleted documents) not equal to to total documents
  //   we need to wait and poll again the task status
  private boolean waitUntilTaskIsCompleted(String taskId, long srcCount) {
    final GetTasksResponse taskResponse = waitTaskCompletion(taskId);

    if (taskResponse != null) {
      logProgress(taskId, taskResponse.response().total(), srcCount);

      final long total = taskResponse.response().total();
      log.info("Source docs: {}, Migrated docs: {}", srcCount, total);
      return total == srcCount;
    } else {
      // need to reindex again
      return false;
    }
  }

  private void logProgress(String taskId, long processed, long srcCount) {
    var progress = processed * 100.00 / srcCount;
    log.info("TaskId: {}, Progress: {}%", taskId, String.format("%.2f", progress));
  }

  public GetIndexResponse get(GetIndexRequest.Builder requestBuilder) {
    GetIndexRequest request = requestBuilder.build();
    return safe(() -> openSearchClient.indices().get(request), e -> "Failed to get index " + request.index());
  }

}

/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.store.opensearch.client.sync;

import static java.lang.String.format;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.schema.IndexMapping;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.elasticsearch.rest.RestStatus;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.indices.*;
import org.opensearch.client.opensearch.indices.get_mapping.IndexMappingRecord;
import org.opensearch.client.opensearch.indices.update_aliases.Action;
import org.opensearch.client.opensearch.indices.update_aliases.AddAction;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.slf4j.Logger;

public class OpenSearchIndexOperations extends OpenSearchRetryOperation {
  public static final String NUMBERS_OF_REPLICA = "index.number_of_replicas";
  public static final String NO_REPLICA = "0";
  public static final String REFRESH_INTERVAL = "index.refresh_interval";
  public static final String NO_REFRESH = "-1";

  private final ObjectMapper objectMapper;

  public OpenSearchIndexOperations(
      final Logger logger,
      final OpenSearchClient openSearchClient,
      final ObjectMapper objectMapper) {
    super(logger, openSearchClient);
    this.objectMapper = objectMapper;
  }

  private static String defaultIndexErrorMessage(final String index) {
    return String.format("Failed to search index: %s", index);
  }

  public Set<String> getIndexNamesWithRetries(final String namePattern) {
    return executeWithRetries(
        "Get indices for " + namePattern,
        () -> {
          try {
            final GetIndexResponse response =
                openSearchClient.indices().get(i -> i.index(namePattern));
            return response.result().keySet();
          } catch (final OpenSearchException e) {
            if (e.status() == RestStatus.NOT_FOUND.getStatus()) {
              return Set.of();
            }
            throw e;
          }
        });
  }

  public Set<String> getAliasesNamesWithRetries(final String namePattern) {
    return executeWithRetries(
        "Get aliases for " + namePattern,
        () -> {
          try {
            final GetAliasResponse response =
                openSearchClient.indices().getAlias(i -> i.index(namePattern));
            return response.result().values().stream()
                .map(a -> a.aliases())
                .map(a -> a.keySet())
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
          } catch (final OpenSearchException e) {
            // NOT_FOUND response means that no aliases were found
            if (e.status() == RestStatus.NOT_FOUND.getStatus()) {
              return Set.of();
            }
            throw e;
          }
        });
  }

  public boolean createIndexWithRetries(final CreateIndexRequest createIndexRequest) {
    return executeWithRetries(
        "CreateIndex " + createIndexRequest.index(),
        () -> {
          if (!indicesExist(createIndexRequest.index())) {
            return openSearchClient.indices().create(createIndexRequest).acknowledged();
          }
          if (createIndexRequest.aliases() != null && !createIndexRequest.aliases().isEmpty()) {
            final String aliasName = createIndexRequest.aliases().keySet().iterator().next();
            if (!aliasExists(aliasName)) {
              final Action action =
                  new Action.Builder()
                      .add(
                          new AddAction.Builder()
                              .alias(aliasName)
                              .index(createIndexRequest.index())
                              .isWriteIndex(false)
                              .build())
                      .build();
              final UpdateAliasesRequest request =
                  new UpdateAliasesRequest.Builder().actions(List.of(action)).build();
              openSearchClient.indices().updateAliases(request);
              logger.info(
                  "Alias is created. Index: {}, alias: {} ", createIndexRequest.index(), aliasName);
            }
          }
          return true;
        });
  }

  private boolean aliasExists(final String aliasName) throws IOException {
    final ExistsAliasRequest aliasExistsReq =
        new ExistsAliasRequest.Builder().name(List.of(aliasName)).build();
    return openSearchClient.indices().existsAlias(aliasExistsReq).value();
  }

  private boolean indicesExist(final String indexPattern) throws IOException {
    return openSearchClient
        .indices()
        .exists(e -> e.index(List.of(indexPattern)).ignoreUnavailable(true).allowNoIndices(false))
        .value();
  }

  public long getNumberOfDocumentsWithRetries(final String... indexPatterns) {
    return executeWithRetries(
        "Count number of documents in " + Arrays.asList(indexPatterns),
        () -> openSearchClient.count(c -> c.index(List.of(indexPatterns))).count());
  }

  public boolean indexExists(final String index) {
    return safe(
        () -> openSearchClient.indices().exists(r -> r.index(index)).value(),
        e -> defaultIndexErrorMessage(index));
  }

  public void refresh(final String indexPattern) {
    final var refreshRequest = new RefreshRequest.Builder().index(List.of(indexPattern)).build();
    try {
      final RefreshResponse refresh = openSearchClient.indices().refresh(refreshRequest);
      if (refresh.shards().failures().size() > 0) {
        logger.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (final Exception ex) {
      logger.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }

  public void refresh(final String... indexPatterns) {
    final var refreshRequest = new RefreshRequest.Builder().index(List.of(indexPatterns)).build();
    try {
      final RefreshResponse refresh = openSearchClient.indices().refresh(refreshRequest);
      if (refresh.shards().failures().size() > 0) {
        logger.warn("Unable to refresh indices: {}", List.of(indexPatterns));
      }
    } catch (final Exception ex) {
      logger.warn(String.format("Unable to refresh indices: %s", List.of(indexPatterns)), ex);
    }
  }

  public void refreshWithRetries(final String indexPattern) {
    executeWithRetries(
        "Refresh " + indexPattern,
        () -> {
          try {
            for (final var index : getFilteredIndices(indexPattern)) {
              openSearchClient.indices().refresh(r -> r.index(List.of(index)));
            }
          } catch (final IOException e) {
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
          for (final var index : getFilteredIndices(indexPattern)) {
            openSearchClient.indices().delete(d -> d.index(List.of(indexPattern)));
          }
          return true;
        });
  }

  public IndexSettings getIndexSettingsWithRetries(final String indexName) {
    return executeWithRetries(
        "GetIndexSettings " + indexName,
        () -> {
          final GetIndicesSettingsResponse response =
              openSearchClient.indices().getSettings(s -> s.index(List.of(indexName)));
          final IndexSettings settings = response.result().get(indexName).settings().index();
          // Opensearch bug where all index settings are in the inner index object
          return (settings.index() == null ? settings : settings.index());
        });
  }

  public Map<String, IndexMapping> getIndexMappings(final String indexNamePattern) {
    return executeWithRetries(
        "GetIndexMappings " + indexNamePattern,
        () -> {
          final Map<String, IndexMapping> mappings = new HashMap<>();
          final GetMappingResponse response =
              openSearchClient.indices().getMapping(s -> s.index(indexNamePattern));
          for (final Map.Entry<String, IndexMappingRecord> indexMapping :
              response.result().entrySet()) {

            final Set<IndexMappingProperty> properties = new HashSet<>();
            for (final Map.Entry<String, Property> entry :
                indexMapping.getValue().mappings().properties().entrySet()) {
              final Property propertyVariant = entry.getValue();
              final String propertyAsJson = toJsonString(propertyVariant);

              final Map<String, Object> indexMappingAsMap =
                  objectMapper.readValue(
                      propertyAsJson, new TypeReference<HashMap<String, Object>>() {});
              properties.add(
                  new IndexMappingProperty()
                      .setName(entry.getKey())
                      .setTypeDefinition(indexMappingAsMap));
            }
            final IndexMapping mapping =
                new IndexMapping().setIndexName(indexMapping.getKey()).setProperties(properties);
            mappings.put(indexMapping.getKey(), mapping);
          }
          return mappings;
        });
  }

  public PutMappingResponse putMapping(final PutMappingRequest putMappingRequest) {
    return executeWithRetries(
        "PutMapping " + putMappingRequest.index(),
        () -> openSearchClient.indices().putMapping(putMappingRequest));
  }

  public Map<String, Object> getIndexSettings(final String indexName) {
    return withExtendedOpenSearchClient(
        extendedOpenSearchClient ->
            safe(
                () ->
                    (Map<String, Object>)
                        extendedOpenSearchClient
                            .arbitraryRequest("GET", "/" + indexName, "{}")
                            .get(indexName),
                e -> format("Failed to get index settings for %s", indexName)));
  }

  public String getOrDefaultRefreshInterval(final String indexName, final String defaultValue) {
    final var refreshIntervalTime = getIndexSettingsWithRetries(indexName).refreshInterval();
    String refreshInterval =
        refreshIntervalTime == null ? defaultValue : refreshIntervalTime.time();
    if (refreshInterval.trim().equals(NO_REFRESH)) {
      refreshInterval = defaultValue;
    }
    return refreshInterval;
  }

  public String getOrDefaultNumbersOfReplica(final String indexName, final String defaultValue) {
    final String numberOfReplicasOriginal =
        getIndexSettingsWithRetries(indexName).numberOfReplicas();
    String numbersOfReplica =
        numberOfReplicasOriginal == null ? defaultValue : numberOfReplicasOriginal;
    if (numbersOfReplica.trim().equals(NO_REPLICA)) {
      numbersOfReplica = defaultValue;
    }
    return numbersOfReplica;
  }

  public PutIndicesSettingsResponse putSettings(final PutIndicesSettingsRequest request)
      throws IOException {
    return openSearchClient.indices().putSettings(request);
  }

  public PutIndicesSettingsResponse setIndexLifeCycle(final String index, final String value)
      throws IOException {
    final var request =
        PutIndicesSettingsRequest.of(b -> b.index(index).settings(s -> s.lifecycleName(value)));
    return putSettings(request);
  }

  public boolean setIndexSettingsFor(final IndexSettings settings, final String indexPattern) {
    return executeWithRetries(
        "SetIndexSettings " + indexPattern,
        () ->
            openSearchClient
                .indices()
                .putSettings(s -> s.index(indexPattern).settings(settings))
                .acknowledged());
  }

  public AnalyzeResponse analyze(final AnalyzeRequest analyzeRequest) throws IOException {
    return openSearchClient.indices().analyze(analyzeRequest);
  }

  // TODO -check lifecycle for openSearch
  /* public boolean putLifeCyclePolicy(final PutLifecyclePolicyRequest putLifecyclePolicyRequest) {

      openSearchClient.indices().

    return executeWithRetries(
        String.format("Put LifeCyclePolicy %s ", putLifecyclePolicyRequest.getName()),
        () ->
            openSearchClient
                .indexLifecycle()
                .putLifecyclePolicy(putLifecyclePolicyRequest, requestOptions)
                .isAcknowledged(),
        null);
  }*/

  // TODO check unused
  public void reindexWithRetries(final ReindexRequest reindexRequest) {
    reindexWithRetries(reindexRequest, true);
  }

  // TODO check unused
  public void reindexWithRetries(
      final ReindexRequest reindexRequest, final boolean checkDocumentCount) {
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
              logger.info("Reindex of {} -> {} is already done.", srcIndices, dstIndex);
              return true;
            }
          }
          final var response = openSearchClient.reindex(reindexRequest);

          if (response.total().equals(srcCount)) {
            final var taskId = response.task() != null ? response.task() : "task:unavailable";
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
  private boolean waitUntilTaskIsCompleted(final String taskId, final long srcCount) {
    final GetTasksResponse taskResponse = waitTaskCompletion(taskId);

    if (taskResponse != null) {
      logProgress(taskId, taskResponse.response().total(), srcCount);

      final long total = taskResponse.response().total();
      logger.info("Source docs: {}, Migrated docs: {}", srcCount, total);
      return total == srcCount;
    } else {
      // need to reindex again
      return false;
    }
  }

  private void logProgress(final String taskId, final long processed, final long srcCount) {
    final var progress = processed * 100.00 / srcCount;
    logger.info("TaskId: {}, Progress: {}%", taskId, String.format("%.2f", progress));
  }

  public GetIndexResponse get(final GetIndexRequest.Builder requestBuilder) {
    final GetIndexRequest request = requestBuilder.build();
    return safe(
        () -> openSearchClient.indices().get(request),
        e -> "Failed to get index " + request.index());
  }
}

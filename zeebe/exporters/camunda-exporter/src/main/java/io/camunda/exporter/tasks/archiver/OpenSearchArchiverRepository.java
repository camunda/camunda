/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static io.camunda.search.schema.SchemaManager.PI_ARCHIVING_BLOCKED_META_KEY;
import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveByIdTaskSupplier.ArchiveDocIdsBatch;
import io.camunda.exporter.tasks.util.DateOfArchivedDocumentsUtil;
import io.camunda.exporter.tasks.util.OpensearchRepository;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.WillCloseWhenClosed;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.reindex.Source;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.generic.Response;
import org.opensearch.client.opensearch.indices.IndexState;
import org.slf4j.Logger;

public final class OpenSearchArchiverRepository extends OpensearchRepository
    implements ArchiverRepository {
  private static final Time REINDEX_SCROLL_TIMEOUT = Time.of(t -> t.time("30s"));
  private static final long AUTO_SLICES = 0; // see OS docs; 0 means auto

  private final int partitionId;
  private final HistoryConfiguration config;
  private final String archiverBlockedMetaIndex;
  private final IndexTemplateDescriptor listViewTemplateDescriptor;
  private final IndexTemplateDescriptor batchOperationTemplateDescriptor;
  private final IndexTemplateDescriptor usageMetricTemplateDescriptor;
  private final IndexTemplateDescriptor usageMetricTUTemplateDescriptor;
  private final IndexTemplateDescriptor decisionInstanceTemplateDescriptor;
  private final Collection<IndexTemplateDescriptor> allTemplatesDescriptors;
  private final CamundaExporterMetrics metrics;
  private final OpenSearchGenericClient genericClient;
  private final Cache<String, String> lifeCyclePolicyApplied;

  public OpenSearchArchiverRepository(
      final int partitionId,
      final HistoryConfiguration config,
      final ExporterResourceProvider resourceProvider,
      @WillCloseWhenClosed final OpenSearchAsyncClient client,
      final OpenSearchGenericClient genericClient,
      final Executor executor,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    super(client, executor, logger);
    this.partitionId = partitionId;
    this.config = config;
    archiverBlockedMetaIndex =
        resourceProvider
            .getIndexDescriptor(TasklistImportPositionIndex.class)
            .getFullQualifiedName();
    allTemplatesDescriptors = resourceProvider.getIndexTemplateDescriptors();
    listViewTemplateDescriptor =
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
    batchOperationTemplateDescriptor =
        resourceProvider.getIndexTemplateDescriptor(BatchOperationTemplate.class);
    usageMetricTemplateDescriptor =
        resourceProvider.getIndexTemplateDescriptor(UsageMetricTemplate.class);
    usageMetricTUTemplateDescriptor =
        resourceProvider.getIndexTemplateDescriptor(UsageMetricTUTemplate.class);
    decisionInstanceTemplateDescriptor =
        resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class);
    this.metrics = metrics;
    this.genericClient = genericClient;
    lifeCyclePolicyApplied = buildLifeCycleAppliedCache(config.getRetention(), logger);
  }

  private static Cache<String, String> buildLifeCycleAppliedCache(
      final RetentionConfiguration config, final Logger logger) {
    return Caffeine.newBuilder()
        .maximumSize(200)
        .expireAfter(
            Expiry.creating(
                (k, v) -> {
                  if (v == null) {
                    return Duration.ZERO;
                  }
                  return DateOfArchivedDocumentsUtil.getRetentionPolicyMinimumAge(
                          config, v.toString())
                      .orElseGet(
                          () -> {
                            logger.debug(
                                "Unknown retention policy '{}', using default cache expiration", v);
                            return Duration.ofHours(1);
                          });
                }))
        .build();
  }

  @Override
  public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch(final int size) {
    try {
      return withArchivingStatus()
          .thenComposeAsync(
              status -> {
                if (status == ArchivingStatus.BLOCKED) {
                  logger.debug("Archiving is currently blocked.");
                  return CompletableFuture.completedFuture(new ArchiveBatch(null, List.of()));
                }
                final var request = createFinishedInstancesSearchRequest(size);

                final var timer = Timer.start();
                return sendRequestAsync(() -> client.search(request, Object.class))
                    .whenCompleteAsync(
                        (ignored, error) -> metrics.measureArchiverSearch(timer), executor)
                    .thenApplyAsync(
                        (response) -> createArchiveBatch(response, ListViewTemplate.END_DATE),
                        executor);
              });
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(
          new ExporterException("Failed to determine if archiving is blocked:", e));
    }
  }

  @Override
  public CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch() {
    final var searchRequest = createFinishedBatchOperationsSearchRequest();

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.search(searchRequest, Object.class))
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenApplyAsync(
            (response) -> createArchiveBatch(response, BatchOperationTemplate.END_DATE), executor);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getUsageMetricTUNextBatch() {
    final var searchRequest =
        createUsageMetricSearchRequest(
            usageMetricTUTemplateDescriptor.getFullQualifiedName(),
            UsageMetricTUTemplate.END_TIME,
            UsageMetricTUTemplate.PARTITION_ID);

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.search(searchRequest, Object.class))
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenApplyAsync(
            response ->
                createArchiveBatch(
                    response,
                    UsageMetricTUTemplate.END_TIME,
                    config.getUsageMetricsRolloverInterval()),
            executor);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getUsageMetricNextBatch() {
    final var searchRequest =
        createUsageMetricSearchRequest(
            usageMetricTemplateDescriptor.getFullQualifiedName(),
            UsageMetricTemplate.END_TIME,
            UsageMetricTemplate.PARTITION_ID);

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.search(searchRequest, Object.class))
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenApplyAsync(
            response ->
                createArchiveBatch(
                    response,
                    UsageMetricTemplate.END_TIME,
                    config.getUsageMetricsRolloverInterval()),
            executor);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getStandaloneDecisionNextBatch() {
    final var searchRequest = createStandaloneDecisionSearchRequest();

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.search(searchRequest, Object.class))
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenApplyAsync(
            response -> createArchiveBatch(response, DecisionInstanceTemplate.EVALUATION_DATE),
            executor);
  }

  @Override
  public CompletableFuture<Void> setIndexLifeCycle(final String destinationIndexName) {
    final var retention = config.getRetention();
    if (!retention.isEnabled()) {
      return CompletableFuture.completedFuture(null);
    }

    final var matchingIndexTemplate =
        allTemplatesDescriptors.stream()
            .filter(
                index -> destinationIndexName.matches(index.getAllVersionsIndexNameRegexPattern()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No matching index template found for " + destinationIndexName));
    final var policyName = getRetentionPolicyName(matchingIndexTemplate.getIndexName(), retention);
    if (policyName.equals(lifeCyclePolicyApplied.getIfPresent(destinationIndexName))) {
      return CompletableFuture.completedFuture(null);
    }

    return applyPolicyToIndices(policyName, destinationIndexName)
        .thenApply(
            ignored -> {
              lifeCyclePolicyApplied.put(destinationIndexName, policyName);
              return null;
            });
  }

  @Override
  public CompletableFuture<Void> setLifeCycleToAllIndexes() {
    final var retention = config.getRetention();
    if (!retention.isEnabled()) {
      return CompletableFuture.completedFuture(null);
    }

    final var requests =
        allTemplatesDescriptors.stream()
            .map(
                template ->
                    applyPolicyToIndices(
                        getRetentionPolicyName(template.getIndexName(), retention),
                        buildHistoricalIndicesPattern(template)))
            .toList();

    return CompletableFuture.allOf(requests.toArray(new CompletableFuture[0]));
  }

  @Override
  public CompletableFuture<Void> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys) {
    final TermsQuery termsQuery = buildIdTermsQuery(idFieldName, processInstanceKeys);
    final var request =
        new DeleteByQueryRequest.Builder()
            .index(sourceIndexName)
            .slices(AUTO_SLICES)
            .conflicts(Conflicts.Proceed)
            .query(q -> q.terms(termsQuery))
            .build();

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.deleteByQuery(request))
        .whenCompleteAsync(
            (response, error) ->
                metrics.measureArchiverDelete(response != null ? response.total() : null, timer),
            executor)
        .thenApplyAsync(DeleteByQueryResponse::total, executor)
        .thenApplyAsync(ok -> null, executor);
  }

  @Override
  public CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys) {
    final var source =
        new Source.Builder()
            .index(sourceIndexName)
            .query(q -> q.terms(buildIdTermsQuery(idFieldName, processInstanceKeys)))
            .build();
    final var request =
        new ReindexRequest.Builder()
            .source(source)
            .dest(dest -> dest.index(destinationIndexName))
            .conflicts(Conflicts.Proceed)
            .scroll(REINDEX_SCROLL_TIMEOUT)
            .slices(AUTO_SLICES)
            .build();

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.reindex(request))
        .whenCompleteAsync(
            (response, error) ->
                metrics.measureArchiverReindex(response != null ? response.total() : null, timer),
            executor)
        .thenApplyAsync(ignored -> null, executor);
  }

  @Override
  public CompletableFuture<Void> moveDocumentsById(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<String> ids,
      final Executor executor) {

    final ArchiveByIdTaskSupplier<FieldValue> taskSupplier =
        new ArchiveByIdTaskSupplier<>(
            sourceIndexName,
            destinationIndexName,
            searchAfter -> getArchiveDocIdsBatch(sourceIndexName, idFieldName, ids, searchAfter),
            this::reindexDocumentsById,
            this::deleteDocumentsById,
            executor,
            logger);

    final var timer = Timer.start();
    return AsyncRepeatUntil.repeatUntil(
            taskSupplier::moveNextBatch, count -> taskSupplier.isComplete())
        .thenComposeAsync(docIds -> setIndexLifeCycle(destinationIndexName), executor)
        .thenApply(
            ignored -> {
              logger.debug(
                  "Successfully completed archiving {} to the {} index, moved {} docs in {}s",
                  sourceIndexName,
                  destinationIndexName,
                  taskSupplier.getTotalArchived(),
                  taskSupplier.getTotalTimeTakenMs() / 1000);

              metrics.measureArchiveIndexDuration(
                  sourceIndexName, timer, taskSupplier.getTotalArchived());
              return ignored;
            })
        .whenComplete(
            (val, err) -> {
              if (err != null) {
                logger.error(
                    "Failed archiving {} to the {} index, moved {} docs so far in {}s, error={}",
                    sourceIndexName,
                    destinationIndexName,
                    taskSupplier.getTotalArchived(),
                    taskSupplier.getTotalTimeTakenMs() / 1000,
                    err.getMessage(),
                    err);
              }
            });
  }

  @Override
  public CompletableFuture<Integer> getCountOfProcessInstancesAwaitingArchival() {
    final var countRequest =
        CountRequest.of(
            cr ->
                cr.index(listViewTemplateDescriptor.getFullQualifiedName())
                    .query(
                        finishedProcessInstancesQuery(
                            config.getArchivingTimePoint(), partitionId)));

    try {
      return client.count(countRequest).thenApplyAsync(res -> Math.toIntExact(res.count()));
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @VisibleForTesting
  CompletableFuture<ArchiveDocIdsBatch<FieldValue>> getArchiveDocIdsBatch(
      final String sourceIndexName,
      final String idFieldName,
      final List<String> ids,
      final List<FieldValue> searchAfter) {
    final TermsQuery termsQuery = buildIdTermsQuery(idFieldName, ids);
    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(sourceIndexName)
            .requestCache(false)
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .query(q -> q.terms(termsQuery))
            .size(config.getReindexBatchSize())
            .source(s -> s.fetch(false))
            .sort(sort -> sort.field(field -> field.field("id").order(SortOrder.Asc)));

    if (searchAfter != null && !searchAfter.isEmpty()) {
      requestBuilder.searchAfterVals(searchAfter);
    }

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.search(requestBuilder.build(), Object.class))
        .whenCompleteAsync(
            (response, error) -> metrics.measureArchiveDocIdsSearchDuration(timer), executor)
        .thenApply(
            response -> {
              final List<Hit<Object>> hits = response.hits().hits();
              if (hits.isEmpty()) {
                return ArchiveDocIdsBatch.empty();
              }
              return ArchiveDocIdsBatch.from(
                  hits.stream().map(Hit::id).toList(), hits.getLast().sortVals());
            });
  }

  @VisibleForTesting
  CompletableFuture<Long> reindexDocumentsById(
      final String sourceIndexName, final String destinationIndexName, final List<String> docIds) {
    if (docIds.isEmpty()) {
      return CompletableFuture.completedFuture(0L);
    }

    final var query = QueryBuilders.bool().filter(b -> b.ids(id -> id.values(docIds))).build();
    final var request =
        new ReindexRequest.Builder()
            .source(src -> src.index(sourceIndexName).query(query.toQuery()))
            .dest(dest -> dest.index(destinationIndexName))
            .conflicts(Conflicts.Proceed)
            .scroll(REINDEX_SCROLL_TIMEOUT)
            .slices(AUTO_SLICES)
            .build();

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.reindex(request))
        .thenApplyAsync(
            response -> {
              validateReindexResponse(sourceIndexName, response);
              return response.total();
            },
            executor)
        .whenCompleteAsync(
            (total, error) -> metrics.measureArchiverReindex(total, timer), executor);
  }

  private static void validateReindexResponse(
      final String sourceIndex, final ReindexResponse response) {
    if (Boolean.TRUE.equals(response.timedOut())) {
      throw new IllegalStateException("Reindex request from %s timed out".formatted(sourceIndex));
    }
    final var failures = response.failures();
    if (!failures.isEmpty()) {
      throw new IllegalStateException(
          "Reindex request from %s index completed with %d failures"
              .formatted(sourceIndex, failures.size()));
    }
  }

  @VisibleForTesting
  CompletableFuture<Long> deleteDocumentsById(
      final String sourceIndexName, final List<String> docIds) {
    if (docIds.isEmpty()) {
      return CompletableFuture.completedFuture(0L);
    }

    final var operations =
        docIds.stream().map(docId -> BulkOperation.of(b -> b.delete(d -> d.id(docId)))).toList();

    final BulkRequest request =
        BulkRequest.of(b -> b.index(sourceIndexName).operations(operations));

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.bulk(request))
        .thenApplyAsync(response -> getDeletedDocCount(sourceIndexName, response), executor)
        .whenCompleteAsync(
            (idsSize, error) -> metrics.measureArchiverDelete(idsSize, timer), executor);
  }

  private long getDeletedDocCount(final String sourceIndex, final BulkResponse response) {
    if (response.errors()) {
      final long errorCount =
          response.items().stream().filter(item -> item.error() != null).count();
      throw new IllegalStateException(
          "Deleting reindexed documents from %s index completed with %d failures"
              .formatted(sourceIndex, errorCount));
    }
    return response.items().size();
  }

  private SearchRequest createUsageMetricSearchRequest(
      final String indexName, final String endTimeField, final String partitionIdField) {
    final var endDateQ =
        QueryBuilders.range()
            .field(endTimeField)
            .lte(JsonData.of(config.getArchivingTimePoint()))
            .build()
            .toQuery();

    final Builder boolBuilder = QueryBuilders.bool();
    boolBuilder.must(endDateQ);

    if (partitionId == START_PARTITION_ID) {
      // Include -1 for migrated documents without partitionId
      final List<FieldValue> partitionIds = List.of(FieldValue.of(-1), FieldValue.of(partitionId));
      final var termsQ =
          QueryBuilders.terms()
              .field(partitionIdField)
              .terms(t -> t.value(partitionIds))
              .build()
              .toQuery();
      boolBuilder.must(termsQ);
    } else {
      final var termQ =
          QueryBuilders.term()
              .field(partitionIdField)
              .value(FieldValue.of(partitionId))
              .build()
              .toQuery();
      boolBuilder.must(termQ);
    }

    return createSearchRequest(indexName, boolBuilder.build().toQuery(), endTimeField);
  }

  private CompletableFuture<ArchivingStatus> withArchivingStatus() throws IOException {
    return client
        .indices()
        .get(r -> r.index(archiverBlockedMetaIndex))
        .thenApply(
            response ->
                Optional.ofNullable(response.result().get(archiverBlockedMetaIndex))
                    .map(IndexState::mappings)
                    .map(m -> m.meta().get(PI_ARCHIVING_BLOCKED_META_KEY))
                    .map(jd -> jd.to(Boolean.class))
                    .map(blocked -> blocked ? ArchivingStatus.BLOCKED : ArchivingStatus.NOT_BLOCKED)
                    .orElse(ArchivingStatus.NOT_BLOCKED));
  }

  private Query finishedProcessInstancesQuery(
      final String archivingTimePoint, final int partitionId) {
    final var endDateQ =
        QueryBuilders.range()
            .field(ListViewTemplate.END_DATE)
            .lte(JsonData.of(archivingTimePoint))
            .build();
    final var isProcessInstanceQ =
        QueryBuilders.term()
            .field(ListViewTemplate.JOIN_RELATION)
            .value(FieldValue.of(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION))
            .build();
    final var partitionQ =
        QueryBuilders.term()
            .field(ListViewTemplate.PARTITION_ID)
            .value(FieldValue.of(partitionId))
            .build();
    return QueryBuilders.bool()
        .filter(endDateQ.toQuery())
        .filter(isProcessInstanceQ.toQuery())
        .filter(partitionQ.toQuery())
        .build()
        .toQuery();
  }

  private CompletableFuture<Void> applyPolicyToIndices(
      final String policyName, final String indexNamePattern) {
    logger.debug("Applying policy '{}' to indices: {}", policyName, indexNamePattern);

    final var jsonpMapper = genericClient._transport().jsonpMapper();

    return sendRequestAsync(
            () -> {
              final var addRequest =
                  Requests.builder()
                      .method("POST")
                      .endpoint("/_plugins/_ism/add/" + indexNamePattern)
                      .json(new AddPolicyRequestBody(policyName), jsonpMapper)
                      .build();
              return genericClient.executeAsync(addRequest);
            })
        .thenComposeAsync(
            resp -> checkIsmResponse(resp, "add", policyName, indexNamePattern), executor)
        .thenComposeAsync(
            ignored ->
                sendRequestAsync(
                    () -> {
                      final var changeRequest =
                          Requests.builder()
                              .method("POST")
                              .endpoint("/_plugins/_ism/change_policy/" + indexNamePattern)
                              .json(new AddPolicyRequestBody(policyName), jsonpMapper)
                              .build();
                      return genericClient.executeAsync(changeRequest);
                    }),
            executor)
        .thenComposeAsync(
            resp -> checkIsmResponse(resp, "change_policy", policyName, indexNamePattern),
            executor);
  }

  private CompletableFuture<Void> checkIsmResponse(
      final Response response,
      final String operation,
      final String policyName,
      final String indexNamePattern) {
    final var status = response.getStatus();

    // change_policy returns 400 or 404 when no managed indices match the resolved pattern —
    // both are harmless since there are simply no managed indices to update.
    if ((status == 400 || status == 404) && "change_policy".equals(operation)) {
      logger.debug(
          "No managed indices to update for pattern '{}' (change_policy '{}' returned {})",
          indexNamePattern,
          policyName,
          status);
      return CompletableFuture.completedFuture(null);
    }

    if (status >= 400) {
      return CompletableFuture.failedFuture(
          new ExporterException(
              "Failed to "
                  + operation
                  + " index lifecycle policy '"
                  + policyName
                  + "' for index: "
                  + indexNamePattern
                  + ".\nStatus: "
                  + status
                  + ", Reason: "
                  + response.getReason()));
    }
    return CompletableFuture.completedFuture(null);
  }

  private SearchRequest createFinishedInstancesSearchRequest(final int size) {
    return createSearchRequest(
        listViewTemplateDescriptor.getFullQualifiedName(),
        finishedProcessInstancesQuery(config.getArchivingTimePoint(), partitionId),
        size,
        ListViewTemplate.END_DATE);
  }

  private SearchRequest createFinishedBatchOperationsSearchRequest() {
    final var endDateQ =
        QueryBuilders.range()
            .field(BatchOperationTemplate.END_DATE)
            .lte(JsonData.of(config.getArchivingTimePoint()))
            .build();

    return createSearchRequest(
        batchOperationTemplateDescriptor.getFullQualifiedName(),
        endDateQ.toQuery(),
        BatchOperationTemplate.END_DATE);
  }

  private SearchRequest createStandaloneDecisionSearchRequest() {
    return createSearchRequest(
        decisionInstanceTemplateDescriptor.getFullQualifiedName(),
        standaloneDecisionInstancesSearchQuery(config.getArchivingTimePoint(), partitionId),
        DecisionInstanceTemplate.EVALUATION_DATE);
  }

  private Query standaloneDecisionInstancesSearchQuery(
      final String archivingTimePoint, final int partitionId) {
    final var endDateQ =
        QueryBuilders.range()
            .field(DecisionInstanceTemplate.EVALUATION_DATE)
            .lte(JsonData.of(archivingTimePoint))
            .build();
    final var partitionQ =
        QueryBuilders.term()
            .field(DecisionInstanceTemplate.PARTITION_ID)
            .value(FieldValue.of(partitionId))
            .build();
    // standalone decision instances have processInstanceKey = -1
    final var standaloneDecisionInstanceQ =
        QueryBuilders.term()
            .field(DecisionInstanceTemplate.PROCESS_INSTANCE_KEY)
            .value(FieldValue.of(-1))
            .build();
    return QueryBuilders.bool()
        .filter(endDateQ.toQuery())
        .filter(partitionQ.toQuery())
        .filter(standaloneDecisionInstanceQ.toQuery())
        .build()
        .toQuery();
  }

  private ArchiveBatch createArchiveBatch(final SearchResponse<?> response, final String field) {
    return createArchiveBatch(response, field, config.getRolloverInterval());
  }

  private ArchiveBatch createArchiveBatch(
      final SearchResponse<?> response, final String field, final String rolloverInterval) {
    final var hits = response.hits().hits();
    if (hits.isEmpty()) {
      return new ArchiveBatch(null, List.of());
    }
    final var endDate = hits.getFirst().fields().get(field).toJson().asJsonArray().getString(0);

    final String date =
        DateOfArchivedDocumentsUtil.getBucketStart(
            endDate, rolloverInterval, config.getElsRolloverDateFormat());

    final var ids =
        hits.stream()
            .takeWhile(
                hit -> hit.fields().get(field).toJson().asJsonArray().getString(0).equals(endDate))
            .map(Hit::id)
            .toList();

    return new ArchiveBatch(date, ids);
  }

  private TermsQuery buildIdTermsQuery(final String idFieldName, final List<String> idValues) {
    return QueryBuilders.terms()
        .field(idFieldName)
        .terms(terms -> terms.value(idValues.stream().map(FieldValue::of).toList()))
        .build();
  }

  private <T> CompletableFuture<T> sendRequestAsync(final RequestSender<T> sender) {
    try {
      return sender.sendRequest();
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(
          new ExporterException(
              "Failed to send request, likely because we failed to parse the request", e));
    }
  }

  private SearchRequest createSearchRequest(
      final String indexName, final Query filterQuery, final String sortField) {
    return createSearchRequest(indexName, filterQuery, config.getRolloverBatchSize(), sortField);
  }

  private SearchRequest createSearchRequest(
      final String indexName, final Query filterQuery, final int size, final String sortField) {
    logger.trace(
        "Create search request against index '{}', with filter '{}' and sortField '{}'",
        indexName,
        filterQuery.toString(),
        sortField);

    return new SearchRequest.Builder()
        .index(indexName)
        .requestCache(false)
        .allowNoIndices(true)
        .ignoreUnavailable(true)
        .source(source -> source.fetch(false))
        .fields(fields -> fields.field(sortField).format(config.getElsRolloverDateFormat()))
        .query(query -> query.bool(q -> q.filter(filterQuery)))
        .sort(sort -> sort.field(field -> field.field(sortField).order(SortOrder.Asc)))
        .size(size)
        .build();
  }

  private record AddPolicyRequestBody(@JsonProperty("policy_id") String policyId) {}

  @FunctionalInterface
  private interface RequestSender<T> {
    CompletableFuture<T> sendRequest() throws IOException;
  }
}

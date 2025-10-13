/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.DateOfArchivedDocumentsUtil;
import io.camunda.exporter.tasks.util.OpensearchRepository;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.reindex.Source;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.opensearch.generic.Requests;
import org.slf4j.Logger;

public final class OpenSearchArchiverRepository extends OpensearchRepository
    implements ArchiverRepository {
  private static final Time REINDEX_SCROLL_TIMEOUT = Time.of(t -> t.time("30s"));
  private static final long AUTO_SLICES = 0; // see OS docs; 0 means auto

  private final int partitionId;
  private final HistoryConfiguration config;
  private final IndexTemplateDescriptor listViewTemplateDescriptor;
  private final IndexTemplateDescriptor batchOperationTemplateDescriptor;
  private final IndexTemplateDescriptor usageMetricTemplateDescriptor;
  private final IndexTemplateDescriptor usageMetricTUTemplateDescriptor;
  private final IndexTemplateDescriptor decisionInstanceTemplateDescriptor;
  private final Collection<IndexTemplateDescriptor> allTemplatesDescriptors;
  private final CamundaExporterMetrics metrics;
  private final OpenSearchGenericClient genericClient;
  private String lastHistoricalArchiverDate = null;
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
  public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch() {
    final var request = createFinishedProcessInstancesSearchRequest();

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.search(request, Object.class))
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenComposeAsync(
            (response) ->
                createArchiveBatch(response, ListViewTemplate.END_DATE, listViewTemplateDescriptor),
            executor);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch() {
    final var searchRequest = createFinishedBatchOperationsSearchRequest();

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.search(searchRequest, Object.class))
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenComposeAsync(
            (response) ->
                createArchiveBatch(
                    response, BatchOperationTemplate.END_DATE, batchOperationTemplateDescriptor),
            executor);
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
        .thenComposeAsync(
            response ->
                createArchiveBatch(
                    response,
                    UsageMetricTUTemplate.END_TIME,
                    usageMetricTUTemplateDescriptor,
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
        .thenComposeAsync(
            response ->
                createArchiveBatch(
                    response,
                    UsageMetricTemplate.END_TIME,
                    usageMetricTemplateDescriptor,
                    config.getUsageMetricsRolloverInterval()),
            executor);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getStandaloneDecisionNextBatch() {
    final var searchRequest = createStandaloneDecisionSearchRequest();

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.search(searchRequest, Object.class))
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenComposeAsync(
            response ->
                createArchiveBatch(
                    response,
                    DecisionInstanceTemplate.EVALUATION_DATE,
                    decisionInstanceTemplateDescriptor),
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
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverDelete(timer), executor)
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
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverReindex(timer), executor)
        .thenApplyAsync(ignored -> null, executor);
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

  private CompletableFuture<List<String>> fetchIndexMatchingIndexes(final String indexPattern)
      throws IOException {
    return client
        .indices()
        .get(b -> b.index(indexPattern))
        .thenApply(r -> new ArrayList<>(r.result().keySet()));
  }

  private CompletableFuture<Void> applyPolicyToIndices(
      final String policyName, final String indexNamePattern) {
    logger.debug("Applying policy '{}' to indices: {}", policyName, indexNamePattern);
    final AddPolicyRequestBody value = new AddPolicyRequestBody(policyName);
    final var request =
        Requests.builder().method("POST").endpoint("_plugins/_ism/add/" + indexNamePattern);
    return sendRequestAsync(
            () ->
                genericClient.executeAsync(
                    request.json(value, genericClient._transport().jsonpMapper()).build()))
        .thenComposeAsync(
            response -> {
              if (response.getStatus() >= 400) {
                return CompletableFuture.failedFuture(
                    new ExporterException(
                        "Failed to set index lifecycle policy '"
                            + policyName
                            + "' for index: "
                            + indexNamePattern
                            + ".\n"
                            + "Status: "
                            + response.getStatus()
                            + ", Reason: "
                            + response.getReason()));
              }
              return CompletableFuture.completedFuture(null);
            },
            executor);
  }

  private SearchRequest createFinishedProcessInstancesSearchRequest() {
    return createSearchRequest(
        listViewTemplateDescriptor.getFullQualifiedName(),
        finishedProcessInstancesQuery(config.getArchivingTimePoint(), partitionId),
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

  private CompletableFuture<ArchiveBatch> createArchiveBatch(
      final SearchResponse<?> response,
      final String field,
      final IndexTemplateDescriptor templateDescriptor) {
    return createArchiveBatch(response, field, templateDescriptor, config.getRolloverInterval());
  }

  private CompletableFuture<ArchiveBatch> createArchiveBatch(
      final SearchResponse<?> response,
      final String field,
      final IndexTemplateDescriptor templateDescriptor,
      final String rolloverInterval) {
    final var hits = response.hits().hits();
    if (hits.isEmpty()) {
      return CompletableFuture.completedFuture(new ArchiveBatch(null, List.of()));
    }
    final var endDate = hits.getFirst().fields().get(field).toJson().asJsonArray().getString(0);

    final CompletableFuture<String> dateFuture;
    try {
      dateFuture =
          (lastHistoricalArchiverDate == null)
              ? DateOfArchivedDocumentsUtil.getLastHistoricalArchiverDate(
                  fetchIndexMatchingIndexes(buildHistoricalIndicesPattern(templateDescriptor)))
              : CompletableFuture.completedFuture(lastHistoricalArchiverDate);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(new ExporterException("Failed to fetch indexes:", e));
    }

    return dateFuture.thenApply(
        date -> {
          lastHistoricalArchiverDate =
              DateOfArchivedDocumentsUtil.calculateDateOfArchiveIndexForBatch(
                  endDate, date, rolloverInterval, config.getElsRolloverDateFormat());

          final var ids =
              hits.stream()
                  .takeWhile(
                      hit ->
                          hit.fields()
                              .get(field)
                              .toJson()
                              .asJsonArray()
                              .getString(0)
                              .equals(endDate))
                  .map(Hit::id)
                  .toList();

          return new ArchiveBatch(lastHistoricalArchiverDate, ids);
        });
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
        .size(config.getRolloverBatchSize())
        .build();
  }

  private record AddPolicyRequestBody(@JsonProperty("policy_id") String policyId) {}

  @FunctionalInterface
  private interface RequestSender<T> {
    CompletableFuture<T> sendRequest() throws IOException;
  }
}

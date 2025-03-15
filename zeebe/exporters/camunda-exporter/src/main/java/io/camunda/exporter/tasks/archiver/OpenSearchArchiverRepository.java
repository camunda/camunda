/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.OpensearchRepository;
import io.camunda.search.schema.configuration.RetentionConfiguration;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import javax.annotation.WillCloseWhenClosed;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.CalendarInterval;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch.cat.indices.IndicesRecord;
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
  private static final String DATES_AGG = "datesAgg";
  private static final String INSTANCES_AGG = "instancesAgg";
  private static final String DATES_SORTED_AGG = "datesSortedAgg";
  private static final Time REINDEX_SCROLL_TIMEOUT = Time.of(t -> t.time("30s"));
  private static final long AUTO_SLICES = 0; // see OS docs; 0 means auto
  private static final String INDEX_WILDCARD = ".+-\\d+\\.\\d+\\.\\d+_.+$";

  private final int partitionId;
  private final HistoryConfiguration config;
  private final RetentionConfiguration retention;
  private final String indexPrefix;
  private final String processInstanceIndex;
  private final String batchOperationIndex;
  private final CamundaExporterMetrics metrics;
  private final OpenSearchGenericClient genericClient;
  private final CalendarInterval rolloverInterval;

  public OpenSearchArchiverRepository(
      final int partitionId,
      final HistoryConfiguration config,
      final RetentionConfiguration retention,
      final String indexPrefix,
      final String processInstanceIndex,
      final String batchOperationIndex,
      @WillCloseWhenClosed final OpenSearchAsyncClient client,
      final Executor executor,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    super(client, executor, logger);
    this.partitionId = partitionId;
    this.config = config;
    this.retention = retention;
    this.indexPrefix = indexPrefix;
    this.processInstanceIndex = processInstanceIndex;
    this.batchOperationIndex = batchOperationIndex;
    this.metrics = metrics;

    genericClient = new OpenSearchGenericClient(client._transport(), client._transportOptions());
    rolloverInterval = mapCalendarInterval(config.getRolloverInterval());
  }

  @Override
  public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch() {
    final var aggregation =
        createFinishedEntityAggregation(ListViewTemplate.END_DATE, ListViewTemplate.ID);
    final var request = createFinishedInstancesSearchRequest(aggregation);

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.search(request, Object.class))
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenApplyAsync(this::createArchiveBatch, executor);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch() {
    final var aggregation =
        createFinishedEntityAggregation(BatchOperationTemplate.END_DATE, BatchOperationTemplate.ID);
    final var searchRequest = createFinishedBatchOperationsSearchRequest(aggregation);

    final var timer = Timer.start();
    return sendRequestAsync(() -> client.search(searchRequest, Object.class))
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenApplyAsync(this::createArchiveBatch, executor);
  }

  @Override
  public CompletableFuture<Void> setIndexLifeCycle(final String... destinationIndexName) {
    if (!retention.isEnabled() || destinationIndexName.length == 0) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.allOf(
        Arrays.stream(destinationIndexName)
            .map(this::applyPolicyToIndex)
            .toArray(CompletableFuture[]::new));
  }

  @Override
  public CompletableFuture<Void> setLifeCycleToAllIndexes() {
    if (!retention.isEnabled()) {
      return CompletableFuture.completedFuture(null);
    }

    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(indexPrefix);
    final var indexWildCard = "^" + formattedPrefix + INDEX_WILDCARD;

    try {
      return fetchIndexMatchingIndexes(indexWildCard)
          .thenComposeAsync(indices -> setIndexLifeCycle(indices.toArray(String[]::new)), executor);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(new ExporterException("Failed to fetch indexes:", e));
    }
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

  private CompletableFuture<List<String>> fetchIndexMatchingIndexes(final String indexWildCard)
      throws IOException {
    final var pattern = Pattern.compile(indexWildCard);
    return client
        .cat()
        .indices()
        .thenApply(
            r ->
                r.valueBody().stream()
                    .map(IndicesRecord::index)
                    .filter(Objects::nonNull)
                    .filter(index -> pattern.matcher(index).matches())
                    .toList());
  }

  private CompletableFuture<Void> applyPolicyToIndex(final String index) {
    final AddPolicyRequestBody value = new AddPolicyRequestBody(retention.getPolicyName());
    final var request = Requests.builder().method("POST").endpoint("_plugins/_ism/add/" + index);
    return sendRequestAsync(
            () ->
                genericClient.executeAsync(
                    request.json(value, genericClient._transport().jsonpMapper()).build()))
        .thenComposeAsync(
            response -> {
              if (response.getStatus() >= 400) {
                return CompletableFuture.failedFuture(
                    new ExporterException(
                        "Failed to set index lifecycle policy for index: "
                            + index
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

  private SearchRequest createFinishedBatchOperationsSearchRequest(final Aggregation aggregation) {
    final var endDateQ =
        QueryBuilders.range()
            .field(BatchOperationTemplate.END_DATE)
            .lte(JsonData.of(config.getArchivingTimePoint()))
            .build();

    return createSearchRequest(
        batchOperationIndex, endDateQ.toQuery(), aggregation, BatchOperationTemplate.END_DATE);
  }

  private ArchiveBatch createArchiveBatch(final SearchResponse<?> search) {
    final var aggregation = search.aggregations().get(DATES_AGG);
    if (aggregation == null) {
      return null;
    }

    final List<DateHistogramBucket> buckets = aggregation.dateHistogram().buckets().array();
    if (buckets.isEmpty()) {
      return null;
    }

    final var bucket = buckets.getFirst();
    final var finishDate = bucket.keyAsString();
    final List<String> ids =
        bucket.aggregations().get(INSTANCES_AGG).topHits().hits().hits().stream()
            .map(Hit::id)
            .toList();
    return new ArchiveBatch(finishDate, ids);
  }

  private TermsQuery buildIdTermsQuery(final String idFieldName, final List<String> idValues) {
    return QueryBuilders.terms()
        .field(idFieldName)
        .terms(terms -> terms.value(idValues.stream().map(FieldValue::of).toList()))
        .build();
  }

  private CalendarInterval mapCalendarInterval(final String alias) {
    return Arrays.stream(CalendarInterval.values())
        .filter(c -> c.aliases() != null)
        .filter(c -> Arrays.binarySearch(c.aliases(), alias) >= 0)
        .findFirst()
        .orElseThrow();
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

  private SearchRequest createFinishedInstancesSearchRequest(final Aggregation aggregation) {
    final var endDateQ =
        QueryBuilders.range()
            .field(ListViewTemplate.END_DATE)
            .lte(JsonData.of(config.getArchivingTimePoint()))
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
    final var combinedQuery =
        QueryBuilders.bool()
            .must(endDateQ.toQuery(), isProcessInstanceQ.toQuery(), partitionQ.toQuery())
            .build();

    return createSearchRequest(
        processInstanceIndex, combinedQuery.toQuery(), aggregation, ListViewTemplate.END_DATE);
  }

  private Aggregation createFinishedEntityAggregation(final String endDate, final String id) {
    final var dateAggregation =
        AggregationBuilders.dateHistogram()
            .field(endDate)
            .calendarInterval(rolloverInterval)
            .format(config.getElsRolloverDateFormat())
            .keyed(false) // get result as an array (not a map)
            .build();
    final var sortAggregation =
        AggregationBuilders.bucketSort()
            .sort(sort -> sort.field(b -> b.field("_key")))
            .size(1) // we want to get only one bucket at a time
            .build();
    final var instanceAggregation =
        AggregationBuilders.topHits()
            .size(config.getRolloverBatchSize())
            .sort(sort -> sort.field(b -> b.field(id).order(SortOrder.Asc)))
            .source(source -> source.filter(filter -> filter.includes(id)))
            .build();
    return new Aggregation.Builder()
        .dateHistogram(dateAggregation)
        .aggregations(DATES_SORTED_AGG, Aggregation.of(b -> b.bucketSort(sortAggregation)))
        .aggregations(INSTANCES_AGG, Aggregation.of(b -> b.topHits(instanceAggregation)))
        .build();
  }

  private SearchRequest createSearchRequest(
      final String indexName,
      final Query filterQuery,
      final Aggregation aggregation,
      final String sortField) {
    logger.trace(
        "Finished entities for archiving request: \n{}\n and aggregation: \n{}",
        filterQuery.toString(),
        aggregation.toString());

    return new SearchRequest.Builder()
        .index(indexName)
        .requestCache(false)
        .allowNoIndices(true)
        .ignoreUnavailable(true)
        .source(source -> source.fetch(false))
        .query(query -> query.constantScore(q -> q.filter(filterQuery)))
        .aggregations(DATES_AGG, aggregation)
        .sort(sort -> sort.field(field -> field.field(sortField).order(SortOrder.Asc)))
        .size(0)
        .build();
  }

  private record AddPolicyRequestBody(@JsonProperty("policy_id") String policyId) {}

  @FunctionalInterface
  private interface RequestSender<T> {
    CompletableFuture<T> sendRequest() throws IOException;
  }
}

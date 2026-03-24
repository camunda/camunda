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

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Slices;
import co.elastic.clients.elasticsearch._types.SlicesCalculation;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsResponse;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.DateOfArchivedDocumentsUtil;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import javax.annotation.WillCloseWhenClosed;
import org.slf4j.Logger;

public final class ElasticsearchArchiverRepository extends ElasticsearchRepository
    implements ArchiverRepository {
  private static final Slices AUTO_SLICES =
      Slices.of(slices -> slices.computed(SlicesCalculation.Auto));
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
  private final Map<String, String> lastHistoricalArchiverDates = new ConcurrentHashMap<>();
  private final Cache<String, String> lifeCyclePolicyApplied;

  public ElasticsearchArchiverRepository(
      final int partitionId,
      final HistoryConfiguration config,
      final ExporterResourceProvider resourceProvider,
      @WillCloseWhenClosed final ElasticsearchAsyncClient client,
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
    return withArchivingStatus()
        .thenComposeAsync(
            status -> {
              if (status == ArchivingStatus.BLOCKED) {
                logger.debug("Archiving is currently blocked.");
                return CompletableFuture.completedFuture(new ArchiveBatch(null, List.of()));
              }
              final var searchRequest = createFinishedInstancesSearchRequest();

              final var timer = Timer.start();
              return client
                  .search(searchRequest, Object.class)
                  .whenCompleteAsync(
                      (ignored, error) -> metrics.measureArchiverSearch(timer), executor)
                  .thenComposeAsync(
                      (response) ->
                          createArchiveBatch(
                              response, ListViewTemplate.END_DATE, listViewTemplateDescriptor),
                      executor);
            });
  }

  @Override
  public CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch() {
    final var searchRequest = createFinishedBatchOperationsSearchRequest();

    final var timer = Timer.start();
    return client
        .search(searchRequest, Object.class)
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
    return client
        .search(searchRequest, Object.class)
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
    return client
        .search(searchRequest, Object.class)
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
    return client
        .search(searchRequest, Object.class)
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
            ok -> {
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
    return client
        .deleteByQuery(request)
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
    final var timer = Timer.start();
    return scrollAndBulkMove(
            sourceIndexName, destinationIndexName, idFieldName, processInstanceKeys)
        .whenCompleteAsync(
            (ignored, error) -> metrics.measureArchiverReindex(null, timer), executor);
  }

  private CompletableFuture<Void> scrollAndBulkMove(
      final String sourceIndex,
      final String destIndex,
      final String idField,
      final List<String> ids) {
    final var termsQuery = buildIdTermsQuery(idField, ids);
    final var request =
        new SearchRequest.Builder()
            .index(sourceIndex)
            .query(q -> q.terms(termsQuery))
            .scroll(SCROLL_KEEP_ALIVE)
            .size(SCROLL_PAGE_SIZE)
            .build();

    return client
        .search(request, ObjectNode.class)
        .thenComposeAsync(
            response ->
                clearScrollOnComplete(
                    response.scrollId(),
                    processScrollPage(
                        response.hits().hits(), response.scrollId(), destIndex)),
            executor);
  }

  private CompletableFuture<Void> processScrollPage(
      final List<Hit<ObjectNode>> hits, final String scrollId, final String destIndex) {
    if (hits.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    final var operations =
        hits.stream()
            .map(
                hit ->
                    BulkOperation.of(
                        op ->
                            op.index(
                                idx ->
                                    idx.index(destIndex)
                                        .id(hit.id())
                                        .routing(hit.routing())
                                        .document(hit.source()))))
            .toList();

    final var bulkRequest = new BulkRequest.Builder().operations(operations).build();

    return client
        .bulk(bulkRequest)
        .thenComposeAsync(
            bulkResponse -> {
              if (bulkResponse.errors()) {
                return CompletableFuture.<Void>failedFuture(
                    collectBulkErrors(bulkResponse.items()));
              }
              return client
                  .scroll(
                      r -> r.scrollId(scrollId).scroll(SCROLL_KEEP_ALIVE), ObjectNode.class)
                  .thenComposeAsync(
                      scrollResp ->
                          processScrollPage(
                              scrollResp.hits().hits(), scrollResp.scrollId(), destIndex),
                      executor);
            },
            executor);
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

    return client.count(countRequest).thenApplyAsync(res -> Math.toIntExact(res.count()));
  }

  private CompletableFuture<ArchivingStatus> withArchivingStatus() {
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
        QueryBuilders.range(
            q -> q.date(d -> d.field(ListViewTemplate.END_DATE).lte(archivingTimePoint)));
    final var isProcessInstanceQ =
        QueryBuilders.term(
            q ->
                q.field(ListViewTemplate.JOIN_RELATION)
                    .value(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION));
    final var partitionQ =
        QueryBuilders.term(q -> q.field(ListViewTemplate.PARTITION_ID).value(partitionId));
    return QueryBuilders.bool(
        q -> q.filter(endDateQ).filter(isProcessInstanceQ).filter(partitionQ));
  }

  private CompletableFuture<List<String>> fetchMatchingIndexes(final String indexPattern) {
    return client
        .indices()
        .get(b -> b.index(indexPattern))
        .thenApplyAsync(response -> new ArrayList<>(response.result().keySet()), executor);
  }

  private SearchRequest createFinishedInstancesSearchRequest() {
    return createSearchRequest(
        listViewTemplateDescriptor.getFullQualifiedName(),
        finishedProcessInstancesQuery(config.getArchivingTimePoint(), partitionId),
        ListViewTemplate.END_DATE);
  }

  private CompletableFuture<PutIndicesSettingsResponse> applyPolicyToIndices(
      final String policyName, final String indexNamePattern) {

    logger.debug("Applying policy '{}' to indices: {}", policyName, indexNamePattern);

    final var settingsRequest =
        new PutIndicesSettingsRequest.Builder()
            .settings(settings -> settings.lifecycle(lifecycle -> lifecycle.name(policyName)))
            .index(indexNamePattern)
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .build();

    return client.indices().putSettings(settingsRequest);
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

    final String endDate = hits.getFirst().fields().get(field).toJson().asJsonArray().getString(0);
    final String templateIndexName = templateDescriptor.getIndexName();
    final String lastHistoricalArchiverDate = lastHistoricalArchiverDates.get(templateIndexName);

    final CompletableFuture<String> dateFuture =
        (lastHistoricalArchiverDate == null)
            ? DateOfArchivedDocumentsUtil.getLastHistoricalArchiverDate(
                fetchMatchingIndexes(buildHistoricalIndicesPattern(templateDescriptor)))
            : CompletableFuture.completedFuture(lastHistoricalArchiverDate);

    return dateFuture.thenApply(
        date -> {
          final String nextArchiverDate =
              DateOfArchivedDocumentsUtil.calculateDateOfArchiveIndexForBatch(
                  endDate, date, rolloverInterval, config.getElsRolloverDateFormat());

          lastHistoricalArchiverDates.put(templateIndexName, nextArchiverDate);

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

          return new ArchiveBatch(nextArchiverDate, ids);
        });
  }

  private TermsQuery buildIdTermsQuery(final String idFieldName, final List<String> idValues) {
    return QueryBuilders.terms()
        .field(idFieldName)
        .terms(terms -> terms.value(idValues.stream().map(FieldValue::of).toList()))
        .build();
  }

  private SearchRequest createFinishedBatchOperationsSearchRequest() {
    final var endDateQ =
        QueryBuilders.range(
            q ->
                q.date(
                    d ->
                        d.field(BatchOperationTemplate.END_DATE)
                            .lte(config.getArchivingTimePoint())));

    return createSearchRequest(
        batchOperationTemplateDescriptor.getFullQualifiedName(),
        endDateQ,
        BatchOperationTemplate.END_DATE);
  }

  private SearchRequest createUsageMetricSearchRequest(
      final String indexName, final String endTimeField, final String partitionIdField) {
    final var endDateQ =
        QueryBuilders.range(
            q -> q.date(d -> d.field(endTimeField).lte(config.getArchivingTimePoint())));

    final Builder boolBuilder = QueryBuilders.bool();
    boolBuilder.must(endDateQ);

    if (partitionId == START_PARTITION_ID) {
      // Include -1 for migrated documents without partitionId
      final List<FieldValue> partitionIds = List.of(FieldValue.of(-1), FieldValue.of(partitionId));
      final var termsQ =
          QueryBuilders.terms(q -> q.field(partitionIdField).terms(t -> t.value(partitionIds)));
      boolBuilder.must(termsQ);
    } else {
      final var termQ = QueryBuilders.term(q -> q.field(partitionIdField).value(partitionId));
      boolBuilder.must(termQ);
    }

    return createSearchRequest(indexName, boolBuilder.build()._toQuery(), endTimeField);
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
        QueryBuilders.range(
            q ->
                q.date(
                    d ->
                        d.field(DecisionInstanceTemplate.EVALUATION_DATE).lte(archivingTimePoint)));
    final var partitionQ =
        QueryBuilders.term(q -> q.field(DecisionInstanceTemplate.PARTITION_ID).value(partitionId));
    // standalone decision instances have processInstanceKey = -1
    final var standaloneDecisionInstanceQ =
        QueryBuilders.term(q -> q.field(DecisionInstanceTemplate.PROCESS_INSTANCE_KEY).value(-1));
    return QueryBuilders.bool(
        q -> q.filter(endDateQ).filter(partitionQ).filter(standaloneDecisionInstanceQ));
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
}

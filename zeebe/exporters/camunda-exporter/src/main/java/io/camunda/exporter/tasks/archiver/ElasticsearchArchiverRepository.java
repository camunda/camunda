/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static io.camunda.search.schema.SchemaManager.PI_ARCHIVING_BLOCKED_META_KEY;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Slices;
import co.elastic.clients.elasticsearch._types.SlicesCalculation;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.ReindexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.reindex.Source;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.DateOfArchivedDocumentsUtil;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.WillCloseWhenClosed;
import org.slf4j.Logger;

public final class ElasticsearchArchiverRepository extends ElasticsearchRepository
    implements ArchiverRepository {
  private static final Time REINDEX_SCROLL_TIMEOUT = Time.of(t -> t.time("30s"));
  private static final Slices AUTO_SLICES =
      Slices.of(slices -> slices.computed(SlicesCalculation.Auto));
  private final int partitionId;
  private final HistoryConfiguration config;
  private final String archiverBlockedMetaIndex;
  private final IndexTemplateDescriptor listViewTemplateDescriptor;
  private final IndexTemplateDescriptor batchOperationTemplateDescriptor;
  private final IndexTemplateDescriptor usageMetricTemplateDescriptor;
  private final IndexTemplateDescriptor usageMetricTUTemplateDescriptor;
  private final Collection<IndexTemplateDescriptor> allTemplatesDescriptors;
  private final CamundaExporterMetrics metrics;
  private String lastHistoricalArchiverDate = null;
  private final Cache<String, String> lifeCyclePolicyApplied =
      Caffeine.newBuilder().maximumSize(200).build();

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
    this.metrics = metrics;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch() {
    if (archivingIsBlocked()) {
      logger.debug("Archiving is currently blocked.");
      return CompletableFuture.completedFuture(new ArchiveBatch(null, List.of()));
    }
    final var searchRequest = createFinishedInstancesSearchRequest();

    final var timer = Timer.start();
    return client
        .search(searchRequest, Object.class)
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
  public CompletableFuture<ArchiveBatch> getUsageMetricNextBatch() {
    final var searchRequest =
        createUsageMetricSearchRequest(
            usageMetricTemplateDescriptor.getFullQualifiedName(), UsageMetricTemplate.END_TIME);

    final var timer = Timer.start();
    return client
        .search(searchRequest, Object.class)
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenComposeAsync(
            response ->
                createArchiveBatch(
                    response, UsageMetricTemplate.END_TIME, usageMetricTemplateDescriptor),
            executor);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getUsageMetricTUNextBatch() {
    final var searchRequest =
        createUsageMetricSearchRequest(
            usageMetricTUTemplateDescriptor.getFullQualifiedName(), UsageMetricTUTemplate.END_TIME);

    final var timer = Timer.start();
    return client
        .search(searchRequest, Object.class)
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenComposeAsync(
            response ->
                createArchiveBatch(
                    response, UsageMetricTUTemplate.END_TIME, usageMetricTUTemplateDescriptor),
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
    return client
        .reindex(request)
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

    return client.count(countRequest).thenApplyAsync(res -> Math.toIntExact(res.count()));
  }

  private boolean archivingIsBlocked() {
    return Optional.ofNullable(
            client
                .indices()
                .get(r -> r.index(archiverBlockedMetaIndex))
                .join()
                .result()
                .get(archiverBlockedMetaIndex))
        .map(IndexState::mappings)
        .map(m -> m.meta().get(PI_ARCHIVING_BLOCKED_META_KEY))
        .map(jd -> jd.to(Boolean.class))
        .orElse(false);
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
    final var hits = response.hits().hits();
    if (hits.isEmpty()) {
      return CompletableFuture.completedFuture(new ArchiveBatch(null, List.of()));
    }

    final String endDate = hits.getFirst().fields().get(field).toJson().asJsonArray().getString(0);

    final CompletableFuture<String> dateFuture =
        (lastHistoricalArchiverDate == null)
            ? DateOfArchivedDocumentsUtil.getLastHistoricalArchiverDate(
                fetchMatchingIndexes(buildHistoricalIndicesPattern(templateDescriptor)))
            : CompletableFuture.completedFuture(lastHistoricalArchiverDate);

    return dateFuture.thenApply(
        date -> {
          lastHistoricalArchiverDate =
              DateOfArchivedDocumentsUtil.calculateDateOfArchiveIndexForBatch(
                  endDate, date, config.getRolloverInterval(), config.getElsRolloverDateFormat());

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
      final String indexName, final String endTimeField) {
    final var endDateQ =
        QueryBuilders.range(
            q -> q.date(d -> d.field(endTimeField).lte(config.getArchivingTimePoint())));
    return createSearchRequest(indexName, endDateQ, endTimeField);
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

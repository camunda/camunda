/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.AuditLogCleanupBatch;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.AuditLogCleanupIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.entities.auditlog.AuditLogCleanupEntity;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ElasticsearchAuditLogArchiverRepository extends ElasticsearchRepository
    implements AuditLogArchiverRepository {

  private final int partitionId;
  private final IndexDescriptor auditLogCleanupIndex;
  private final IndexTemplateDescriptor auditLogTemplateDescriptor;
  private final InstantSource clock;
  private final HistoryConfiguration historyConfig;

  public ElasticsearchAuditLogArchiverRepository(
      final int partitionId,
      final ElasticsearchAsyncClient client,
      final Executor executor,
      final Logger logger,
      final IndexDescriptor auditLogCleanupIndex,
      final IndexTemplateDescriptor auditLogTemplateDescriptor,
      final HistoryConfiguration historyConfig,
      final InstantSource clock) {
    super(client, executor, logger);
    this.partitionId = partitionId;
    this.auditLogCleanupIndex = auditLogCleanupIndex;
    this.historyConfig = historyConfig;
    this.auditLogTemplateDescriptor = auditLogTemplateDescriptor;
    this.clock = clock;
  }

  @Override
  public CompletableFuture<AuditLogCleanupBatch> getNextBatch() {
    final var searchRequest = createAuditLogCleanupEntitiesSearchRequest();

    return client
        .search(searchRequest, AuditLogCleanupEntity.class)
        .thenComposeAsync(
            (cleanupResponse) -> {
              final var cleanupHits = cleanupResponse.hits().hits();
              if (cleanupHits.isEmpty()) {
                return CompletableFuture.completedFuture(
                    new AuditLogCleanupBatch(null, List.of(), List.of()));
              }
              final var cleanupEntities = cleanupHits.stream().map(Hit::source).toList();
              final var cleanupEntityIds = cleanupHits.stream().map(Hit::id).toList();

              return client
                  .search(createAuditLogEntitiesSearchRequest(cleanupEntities), Object.class)
                  .thenComposeAsync(
                      auditLogResponse -> {
                        final var finishDate =
                            LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_LOCAL_DATE);
                        final var auditLogHits = auditLogResponse.hits().hits();
                        if (auditLogHits.isEmpty()) {
                          return CompletableFuture.completedFuture(
                              new AuditLogCleanupBatch(finishDate, cleanupEntityIds, List.of()));
                        }

                        final var auditLogIds = auditLogHits.stream().map(Hit::id).toList();
                        return CompletableFuture.completedFuture(
                            new AuditLogCleanupBatch(finishDate, cleanupEntityIds, auditLogIds));
                      });
            },
            executor);
  }

  @Override
  public CompletableFuture<Integer> deleteAuditLogCleanupMetadata(
      final AuditLogCleanupBatch batch) {
    final var bulkRequestBuilder = new BulkRequest.Builder();
    final var sourceIndexName = auditLogCleanupIndex.getFullQualifiedName();
    final var ids = batch.auditLogCleanupIds();

    ids.forEach(
        id -> bulkRequestBuilder.operations(op -> op.delete(d -> d.index(sourceIndexName).id(id))));

    return client
        .bulk(bulkRequestBuilder.build())
        .thenComposeAsync(
            response -> {
              if (response.errors()) {
                final var errorMessage =
                    "Bulk deleting documents from index '%s' by ids '%s' failed with errors: %s"
                        .formatted(sourceIndexName, ids, response.items());
                logger.error(errorMessage);
                return CompletableFuture.failedFuture(new RuntimeException(errorMessage));
              }
              final var deleted = response.items().size();
              return CompletableFuture.completedFuture(deleted);
            },
            executor);
  }

  private SearchRequest createAuditLogCleanupEntitiesSearchRequest() {
    final var indexName = auditLogCleanupIndex.getFullQualifiedName();
    logger.trace("Create search request against index '{}'", indexName);

    return new SearchRequest.Builder()
        .index(indexName)
        .requestCache(false)
        .size(historyConfig.getRolloverBatchSize())
        .sort(s -> s.field(f -> f.field(AuditLogCleanupIndex.ID).order(SortOrder.Asc)))
        .query(q -> q.term(t -> t.field(AuditLogCleanupIndex.PARTITION_ID).value(partitionId)))
        .build();
  }

  private SearchRequest createAuditLogEntitiesSearchRequest(
      final List<AuditLogCleanupEntity> entities) {
    final var indexName = auditLogTemplateDescriptor.getFullQualifiedName();
    logger.trace("Create search request against index '{}'", indexName);

    // TODO should we limit the size of the archive request here? This could be a loooot of
    // documents.
    return new SearchRequest.Builder()
        .index(indexName)
        .requestCache(false)
        .source(s -> s.filter(f -> f.includes(AuditLogTemplate.ID)))
        .query(
            q ->
                q.bool(
                    b -> {
                      filterForEntitiesWithoutEntityType(b, entities);
                      filterForEntitiesWithEntityType(b, entities);
                      return b;
                    }))
        .build();
  }

  private void filterForEntitiesWithoutEntityType(
      final Builder b, final List<AuditLogCleanupEntity> entities) {
    final var entitiesWithoutType =
        entities.stream()
            .filter(e -> e.getEntityType() == null)
            .collect(
                Collectors.groupingBy(
                    AuditLogCleanupEntity::getKeyField,
                    Collectors.mapping(
                        entity -> FieldValue.of(entity.getKey()), Collectors.toList())));

    entitiesWithoutType.forEach(
        (keyField, keys) ->
            b.should(s -> s.terms(tm -> tm.field(keyField).terms(tv -> tv.value(keys)))));
  }

  private void filterForEntitiesWithEntityType(
      final Builder b, final List<AuditLogCleanupEntity> entities) {
    final var entitiesWithType =
        entities.stream()
            .filter(e -> e.getEntityType() != null)
            .collect(
                Collectors.groupingBy(
                    AuditLogCleanupEntity::getEntityType,
                    Collectors.groupingBy(
                        AuditLogCleanupEntity::getKeyField,
                        Collectors.mapping(
                            entity -> FieldValue.of(entity.getKey()), Collectors.toList()))));

    entitiesWithType.forEach(
        (entityType, keyFieldMap) ->
            keyFieldMap.forEach(
                (keyField, keys) ->
                    b.should(
                        s ->
                            s.bool(
                                sb -> {
                                  sb.must(
                                      m ->
                                          m.terms(
                                              tm ->
                                                  tm.field(keyField).terms(tv -> tv.value(keys))));
                                  sb.must(
                                      m ->
                                          m.term(
                                              t ->
                                                  t.field(AuditLogTemplate.ENTITY_TYPE)
                                                      .value(FieldValue.of(entityType.name()))));
                                  return sb;
                                }))));
  }
}

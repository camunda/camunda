/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process.adapter.es;

import static java.util.stream.Collectors.toList;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.process.adapter.Adapter;
import io.camunda.migration.process.adapter.MigrationRepositoryIndex;
import io.camunda.migration.process.adapter.ProcessorStep;
import io.camunda.migration.process.config.ProcessMigrationProperties;
import io.camunda.migration.process.util.AdapterRetryDecorator;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ImportPositionEntity;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ElasticsearchAdapter implements Adapter {

  private final ElasticsearchClient client;
  private final ProcessMigrationProperties properties;
  private final MigrationRepositoryIndex migrationRepositoryIndex;
  private final ProcessIndex processIndex;
  private final ImportPositionIndex importPositionIndex;
  private final AdapterRetryDecorator retryDecorator;

  public ElasticsearchAdapter(
      final ProcessMigrationProperties properties,
      final ConnectConfiguration connectConfiguration) {
    this.properties = properties;
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(connectConfiguration.getIndexPrefix(), true);
    processIndex = new ProcessIndex(connectConfiguration.getIndexPrefix(), true);
    importPositionIndex = new ImportPositionIndex(connectConfiguration.getIndexPrefix(), true);
    client = new ElasticsearchConnector(connectConfiguration).createClient();
    retryDecorator = new AdapterRetryDecorator(properties);
  }

  @Override
  public String migrate(final List<ProcessEntity> entities) throws MigrationException {
    final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
    final var idList = entities.stream().map(ProcessEntity::getId).toList();
    entities.forEach(entity -> migrateEntity(entity, bulkRequestBuilder));
    final BulkResponse response;
    try {
      final BulkRequest bulkRequest = bulkRequestBuilder.build();
      response =
          retryDecorator.decorate(
              "Migrate entities %s".formatted(idList),
              () -> client.bulk(bulkRequest),
              (res) ->
                  res == null
                      || res.items().isEmpty()
                      || res.items().stream().allMatch(i -> i.error() != null));
    } catch (final Exception e) {
      throw new MigrationException("Failed to migrate entities %s".formatted(idList), e);
    }
    return lastUpdatedProcessDefinition(response.items());
  }

  @Override
  public List<ProcessEntity> nextBatch(final String lastMigratedEntity) throws MigrationException {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(processIndex.getFullQualifiedName())
            .size(properties.getBatchSize())
            .sort(s -> s.field(f -> f.field(PROCESS_DEFINITION_KEY).order(SortOrder.Asc)))
            .query(
                q ->
                    q.range(
                        m ->
                            m.term(
                                n ->
                                    n.field(PROCESS_DEFINITION_KEY)
                                        .gt(lastMigratedEntity == null ? "" : lastMigratedEntity))))
            .build();
    final SearchResponse<ProcessEntity> searchResponse;
    try {
      searchResponse =
          retryDecorator.decorate(
              "Fetching next process batch",
              () -> client.search(searchRequest, ProcessEntity.class),
              res -> res.timedOut() || Boolean.TRUE.equals(res.terminatedEarly()));
    } catch (final Exception e) {
      throw new MigrationException("Failed to fetch next processes batch", e);
    }

    return searchResponse.hits().hits().stream().map(Hit::source).collect(toList());
  }

  @Override
  public String readLastMigratedEntity() throws MigrationException {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(migrationRepositoryIndex.getFullQualifiedName())
            .size(1)
            .query(
                q ->
                    q.bool(
                        b ->
                            b.must(
                                    m ->
                                        m.match(
                                            t ->
                                                t.field(MigrationRepositoryIndex.TYPE)
                                                    .query(PROCESSOR_STEP_TYPE)))
                                .must(
                                    m ->
                                        m.term(
                                            t ->
                                                t.field(MigrationRepositoryIndex.ID)
                                                    .value(PROCESSOR_STEP_ID)))))
            .build();
    final SearchResponse<ProcessorStep> searchResponse;

    try {
      searchResponse =
          retryDecorator.decorate(
              "Fetching last migrated process",
              () -> client.search(searchRequest, ProcessorStep.class),
              res -> res.timedOut() || Boolean.TRUE.equals(res.terminatedEarly()));
    } catch (final Exception e) {
      throw new MigrationException("Failed to fetch last migrated process", e);
    }

    return searchResponse.hits().hits().stream()
        .map(Hit::source)
        .filter(Objects::nonNull)
        .map(ProcessorStep::getContent)
        .findFirst()
        .orElse(null);
  }

  @Override
  public void writeLastMigratedEntity(final String processDefinitionKey) throws MigrationException {
    final ProcessorStep currentStep = processorStepForKey(processDefinitionKey);
    final UpdateRequest<ProcessorStep, ProcessorStep> updateRequest =
        new UpdateRequest.Builder<ProcessorStep, ProcessorStep>()
            .index(migrationRepositoryIndex.getFullQualifiedName())
            .id(PROCESSOR_STEP_ID)
            .docAsUpsert(true)
            .doc(currentStep)
            .refresh(Refresh.True)
            .upsert(currentStep)
            .build();

    try {
      retryDecorator.decorate(
          "Update last migrated process",
          () -> client.update(updateRequest, ProcessorStep.class),
          res -> res.result() != Result.Created && res.result() != Result.Updated);
    } catch (final Exception e) {
      throw new MigrationException("Failed to update migrated process", e);
    }
  }

  @Override
  public Set<ImportPositionEntity> readImportPosition() throws MigrationException {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .size(100)
            .index(importPositionIndex.getFullQualifiedName())
            .query(
                q ->
                    q.wildcard(
                        w -> w.field(ImportPositionIndex.ID).value("*-" + ProcessIndex.INDEX_NAME)))
            .build();
    final SearchResponse<ImportPositionEntity> searchResponse;

    try {
      searchResponse =
          retryDecorator.decorate(
              "Fetching import position",
              () -> client.search(searchRequest, ImportPositionEntity.class),
              res -> res.timedOut() || Boolean.TRUE.equals(res.terminatedEarly()));
    } catch (final Exception e) {
      throw new MigrationException("Failed to fetch import position", e);
    }

    return searchResponse.hits().hits().stream()
        .map(Hit::source)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  private void migrateEntity(final ProcessEntity entity, final BulkRequest.Builder bulkRequest) {
    bulkRequest.operations(
        op ->
            op.update(
                e ->
                    e.index(processIndex.getFullQualifiedName())
                        .id(entity.getId())
                        .action(act -> act.doc(getUpdateMap(entity)))));
  }

  private String lastUpdatedProcessDefinition(final List<BulkResponseItem> items) {
    final var sorted = items.stream().sorted(Comparator.comparing(BulkResponseItem::id)).toList();
    for (int i = 0; i < sorted.size(); i++) {
      if (sorted.get(i).error() != null) {
        return i == 0 ? null : Objects.requireNonNull(sorted.get(i - 1).id());
      }
    }

    return Objects.requireNonNull(sorted.getLast().id());
  }
}

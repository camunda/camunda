/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process.adapter.os;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.process.adapter.Adapter;
import io.camunda.migration.process.adapter.MigrationRepositoryIndex;
import io.camunda.migration.process.adapter.ProcessorStep;
import io.camunda.migration.process.config.ProcessMigrationProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.util.retry.RetryDecorator;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.generic.OpenSearchClientException;

public class OpensearchAdapter implements Adapter {

  private final ProcessMigrationProperties properties;
  private final OpenSearchClient client;
  private final MigrationRepositoryIndex migrationRepositoryIndex;
  private final ProcessIndex processIndex;
  private final ImportPositionIndex importPositionIndex;
  private final RetryDecorator retryDecorator;

  public OpensearchAdapter(
      final ProcessMigrationProperties properties,
      final ConnectConfiguration connectConfiguration) {
    this.properties = properties;
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(connectConfiguration.getIndexPrefix(), false);
    processIndex = new ProcessIndex(connectConfiguration.getIndexPrefix(), false);
    importPositionIndex = new ImportPositionIndex(connectConfiguration.getIndexPrefix(), false);
    client = new OpensearchConnector(connectConfiguration).createClient();
    retryDecorator =
        new RetryDecorator(properties.getRetry())
            .withRetryOnException(
                e ->
                    e instanceof IOException
                        || e instanceof OpenSearchException
                        || e instanceof OpenSearchClientException);
  }

  @Override
  public String migrate(final List<ProcessEntity> entities) throws MigrationException {
    final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
    final var idList = entities.stream().map(ProcessEntity::getId).toList();
    entities.forEach(e -> migrateEntity(e, bulkRequestBuilder));

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
  public List<ProcessEntity> nextBatch(final String processDefinitionKey) {
    final SearchRequest request =
        new SearchRequest.Builder()
            .index(processIndex.getFullQualifiedName())
            .size(properties.getBatchSize())
            .sort(s -> s.field(f -> f.field(PROCESS_DEFINITION_KEY).order(SortOrder.Asc)))
            .query(
                q ->
                    q.range(
                        r ->
                            r.field(PROCESS_DEFINITION_KEY)
                                .gt(
                                    JsonData.of(
                                        processDefinitionKey == null ? "" : processDefinitionKey))))
            .build();

    final SearchResponse<ProcessEntity> searchResponse;
    try {
      searchResponse =
          retryDecorator.decorate(
              "Fetching next process batch",
              () -> client.search(request, ProcessEntity.class),
              res -> res.timedOut() || Boolean.TRUE.equals(res.terminatedEarly()));
    } catch (final Exception e) {
      throw new MigrationException("Failed to fetch next processes batch", e);
    }
    return searchResponse.hits().hits().stream().map(Hit::source).toList();
  }

  @Override
  public String readLastMigratedEntity() throws MigrationException {
    final SearchRequest request =
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
                                                    .query(FieldValue.of(PROCESSOR_STEP_TYPE))))
                                .must(
                                    m ->
                                        m.term(
                                            t ->
                                                t.field(MigrationRepositoryIndex.ID)
                                                    .value(FieldValue.of(PROCESSOR_STEP_ID))))))
            .build();

    final SearchResponse<ProcessorStep> searchResponse;

    try {
      searchResponse =
          retryDecorator.decorate(
              "Fetching last migrated process",
              () -> client.search(request, ProcessorStep.class),
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
    final SearchRequest request =
        new SearchRequest.Builder()
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
              () -> client.search(request, ImportPositionEntity.class),
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
                        .document(getUpdateMap(entity))));
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

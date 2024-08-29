/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static io.camunda.exporter.utils.BatchRequestBuilderUtils.bulkOperationBuilder;
import static io.camunda.exporter.utils.BatchRequestBuilderUtils.indexBuilder;
import static io.camunda.exporter.utils.BatchRequestBuilderUtils.indexWithRoutingBuilder;
import static io.camunda.exporter.utils.BatchRequestBuilderUtils.updateActionBuilder;
import static io.camunda.exporter.utils.BatchRequestBuilderUtils.updateOperationBuilder;
import static io.camunda.exporter.utils.BatchRequestBuilderUtils.upsertWithDocBuilder;
import static io.camunda.exporter.utils.BatchRequestBuilderUtils.upsertWithScriptBuilder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.UpdateAction;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import io.camunda.exporter.entities.ExporterEntity;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchBatchRequest implements BatchRequest {
  public static final int UPDATE_RETRY_COUNT = 3;

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchBatchRequest.class);
  private final ElasticsearchClient esClient;
  private final BulkRequest.Builder bulkRequestBuilder;
  private final ElasticsearchScriptBuilder scriptBuilder;

  public ElasticsearchBatchRequest(
      final ElasticsearchClient esClient,
      final BulkRequest.Builder bulkRequestBuilder,
      final ElasticsearchScriptBuilder scriptBuilder) {
    this.esClient = esClient;
    this.bulkRequestBuilder = bulkRequestBuilder;
    this.scriptBuilder = scriptBuilder;
  }

  @Override
  public BatchRequest add(final String index, final ExporterEntity entity)
      throws PersistenceException {
    return addWithId(index, entity.getId(), entity);
  }

  @Override
  public BatchRequest addWithId(final String index, final String id, final ExporterEntity entity)
      throws PersistenceException {
    LOGGER.debug("Add index request for index {} id {} and entity {} ", index, id, entity);
    final var idx = indexBuilder(index, id, entity).build();
    final var op = bulkOperationBuilder(idx).build();
    bulkRequestBuilder.operations(op);
    return this;
  }

  @Override
  public BatchRequest addWithRouting(
      final String index, final ExporterEntity entity, final String routing)
      throws PersistenceException {
    LOGGER.debug(
        "Add index request with routing {} for index {} and entity {} ", routing, index, entity);
    final var idx = indexWithRoutingBuilder(index, entity, routing).build();
    final var op = bulkOperationBuilder(idx).build();
    bulkRequestBuilder.operations(op);
    return this;
  }

  @Override
  public BatchRequest upsert(
      final String index,
      final String id,
      final ExporterEntity entity,
      final Map<String, Object> updateFields)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request for index {} id {} entity {} and update fields {}",
        index,
        id,
        entity,
        updateFields);
    final var upsert = upsertWithDocBuilder(entity, updateFields).build();
    final var updateOperation = updateOperationBuilder(index, id, upsert).build();
    final var op = bulkOperationBuilder(updateOperation).build();
    bulkRequestBuilder.operations(op);

    return this;
  }

  @Override
  public BatchRequest upsertWithRouting(
      final String index,
      final String id,
      final ExporterEntity entity,
      final Map<String, Object> updateFields,
      final String routing)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request with routing {} for index {} id {} entity {} and update fields {}",
        routing,
        index,
        id,
        entity,
        updateFields);
    final var upsert = upsertWithDocBuilder(entity, updateFields).build();
    final var updateOperation = updateOperationBuilder(index, id, upsert).routing(routing).build();
    final var op = bulkOperationBuilder(updateOperation).build();
    bulkRequestBuilder.operations(op);

    return this;
  }

  @Override
  public BatchRequest upsertWithScript(
      final String index,
      final String id,
      final ExporterEntity entity,
      final String script,
      final Map<String, Object> parameters)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request with for index {} id {} entity {} and script {} with parameters {} ",
        index,
        id,
        entity,
        script,
        parameters);

    final Script scriptWithParameters = scriptBuilder.getScriptWithParameters(script, parameters);
    final var upsert = upsertWithScriptBuilder(entity, scriptWithParameters).build();
    final var updateOperation = updateOperationBuilder(index, id, upsert).build();
    final var op = bulkOperationBuilder(updateOperation).build();
    bulkRequestBuilder.operations(op);

    return this;
  }

  @Override
  public BatchRequest upsertWithScriptAndRouting(
      final String index,
      final String id,
      final ExporterEntity entity,
      final String script,
      final Map<String, Object> parameters,
      final String routing)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request with routing {} for index {} id {} entity {} and script {} with parameters {} ",
        routing,
        index,
        id,
        entity,
        script,
        parameters);

    final Script scriptWithParameters = scriptBuilder.getScriptWithParameters(script, parameters);
    final var upsert = upsertWithScriptBuilder(entity, scriptWithParameters).build();
    final var updateOperation = updateOperationBuilder(index, id, upsert).routing(routing).build();
    final var op = bulkOperationBuilder(updateOperation).build();
    bulkRequestBuilder.operations(op);

    return this;
  }

  @Override
  public BatchRequest update(
      final String index, final String id, final Map<String, Object> updateFields)
      throws PersistenceException {
    LOGGER.debug(
        "Add update request for index {} id {} and update fields {}", index, id, updateFields);

    final var update = updateActionBuilder().doc(updateFields).build();
    final var updateOperation = updateOperationBuilder(index, id, update).build();
    final var op = bulkOperationBuilder(updateOperation).build();
    bulkRequestBuilder.operations(op);

    return this;
  }

  @Override
  public BatchRequest update(final String index, final String id, final ExporterEntity entity)
      throws PersistenceException {
    LOGGER.debug("Add update request for index {} id {} and entity {}", index, id, entity);

    final var update =
        new UpdateAction.Builder<ExporterEntity, ExporterEntity>().doc(entity).build();
    final var updateOperation =
        new UpdateOperation.Builder<ExporterEntity, ExporterEntity>()
            .index(index)
            .id(id)
            .action(update)
            .retryOnConflict(UPDATE_RETRY_COUNT)
            .build();
    final var op = new BulkOperation.Builder().update(updateOperation).build();
    bulkRequestBuilder.operations(op);

    return this;
  }

  @Override
  public BatchRequest updateWithScript(
      final String index,
      final String id,
      final String script,
      final Map<String, Object> parameters)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request with for index {} id {} and script {} with parameters {} ",
        index,
        id,
        script,
        parameters);

    final Script scriptWithParameters = scriptBuilder.getScriptWithParameters(script, parameters);
    final var upsert = updateActionBuilder().script(scriptWithParameters).build();
    final var updateOperation = updateOperationBuilder(index, id, upsert).build();
    final var op = bulkOperationBuilder(updateOperation).build();
    bulkRequestBuilder.operations(op);

    return this;
  }

  @Override
  public void execute() throws PersistenceException {
    execute(false);
  }

  @Override
  public void executeWithRefresh() throws PersistenceException {
    execute(true);
  }

  private void execute(final boolean shouldRefresh) throws PersistenceException {
    if (shouldRefresh) {
      bulkRequestBuilder.refresh(Refresh.True);
    }
    final BulkRequest bulkRequest = bulkRequestBuilder.build();
    try {
      LOGGER.debug("************* FLUSH BULK START *************");
      final BulkResponse bulkResponse = esClient.bulk(bulkRequest);
      final List<BulkResponseItem> items = bulkResponse.items();
      for (final BulkResponseItem item : items) {
        if (item.error() != null) {
          LOGGER.error(
              "Bulk request execution failed. {}. Cause: {}.", item, item.error().reason());
          throw new PersistenceException("Operation failed: " + item.error().reason());
        }
      }
      LOGGER.debug("************* FLUSH BULK FINISH *************");
    } catch (final IOException ex) {
      throw new PersistenceException(
          "Error when processing bulk request against Elasticsearch: " + ex.getMessage(), ex);
    }
  }
}

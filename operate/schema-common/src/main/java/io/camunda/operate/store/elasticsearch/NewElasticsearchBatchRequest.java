/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ElasticsearchScriptBuilder;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch request that uses new style ElasticsearchClient instead of deprecated RestHighLevelClient.
 */
public class NewElasticsearchBatchRequest implements BatchRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(NewElasticsearchBatchRequest.class);

  private final ElasticsearchClient esClient;
  private final BulkRequest.Builder bulkRequestBuilder;
  private final ElasticsearchScriptBuilder esScriptBuilder;

  public NewElasticsearchBatchRequest(
      final ElasticsearchClient esClient,
      final BulkRequest.Builder bulkRequestBuilder,
      final ElasticsearchScriptBuilder esScriptBuilder) {
    this.esClient = esClient;
    this.bulkRequestBuilder = bulkRequestBuilder;
    this.esScriptBuilder = esScriptBuilder;
  }

  @Override
  public BatchRequest add(final String index, final OperateEntity entity)
      throws PersistenceException {
    return addWithId(index, entity.getId(), entity);
  }

  @Override
  public BatchRequest addWithId(final String index, final String id, final OperateEntity entity)
      throws PersistenceException {
    LOGGER.debug("Add index request for index {} id {} and entity {} ", index, id, entity);
    bulkRequestBuilder.operations(op -> op.index(idx -> idx.index(index).id(id).document(entity)));
    return this;
  }

  @Override
  public BatchRequest addWithRouting(
      final String index, final OperateEntity entity, final String routing)
      throws PersistenceException {
    LOGGER.debug(
        "Add index request with routing {} for index {} and entity {} ", routing, index, entity);
    bulkRequestBuilder.operations(
        op ->
            op.index(idx -> idx.index(index).id(entity.getId()).routing(routing).document(entity)));
    return this;
  }

  @Override
  public BatchRequest upsert(
      final String index,
      final String id,
      final OperateEntity entity,
      final Map<String, Object> updateFields)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request for index {} id {} entity {} and update fields {}",
        index,
        id,
        entity,
        updateFields);

    final var updateOperation =
        new UpdateOperation.Builder<OperateEntity, Map<String, Object>>()
            .index(index)
            .id(id)
            .action(a -> a.doc(updateFields).upsert(entity))
            .retryOnConflict(ElasticsearchUtil.UPDATE_RETRY_COUNT)
            .build();
    bulkRequestBuilder.operations(op -> op.update(updateOperation));

    return this;
  }

  @Override
  public BatchRequest upsertWithRouting(
      final String index,
      final String id,
      final OperateEntity entity,
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

    final var updateOperation =
        new UpdateOperation.Builder<OperateEntity, Map<String, Object>>()
            .index(index)
            .id(id)
            .routing(routing)
            .action(a -> a.doc(updateFields).upsert(entity))
            .retryOnConflict(ElasticsearchUtil.UPDATE_RETRY_COUNT)
            .build();
    bulkRequestBuilder.operations(op -> op.update(updateOperation));

    return this;
  }

  @Override
  public BatchRequest upsertWithScript(
      final String index,
      final String id,
      final OperateEntity entity,
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

    final var updateOperation =
        new UpdateOperation.Builder<OperateEntity, Map<String, Object>>()
            .index(index)
            .id(id)
            .action(
                a ->
                    a.script(esScriptBuilder.getScriptWithParameters(script, parameters))
                        .upsert(entity))
            .retryOnConflict(ElasticsearchUtil.UPDATE_RETRY_COUNT)
            .build();
    bulkRequestBuilder.operations(op -> op.update(updateOperation));

    return this;
  }

  @Override
  public BatchRequest upsertWithScriptAndRouting(
      final String index,
      final String id,
      final OperateEntity entity,
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

    final var updateOperation =
        new UpdateOperation.Builder<OperateEntity, Map<String, Object>>()
            .index(index)
            .id(id)
            .routing(routing)
            .action(
                a ->
                    a.script(esScriptBuilder.getScriptWithParameters(script, parameters))
                        .upsert(entity))
            .retryOnConflict(ElasticsearchUtil.UPDATE_RETRY_COUNT)
            .build();
    bulkRequestBuilder.operations(op -> op.update(updateOperation));

    return this;
  }

  @Override
  public BatchRequest update(
      final String index, final String id, final Map<String, Object> updateFields)
      throws PersistenceException {
    LOGGER.debug(
        "Add update request for index {} id {} and update fields {}", index, id, updateFields);

    final var updateOperation =
        new UpdateOperation.Builder<OperateEntity, Map<String, Object>>()
            .index(index)
            .id(id)
            .action(a -> a.doc(updateFields))
            .retryOnConflict(ElasticsearchUtil.UPDATE_RETRY_COUNT)
            .build();
    bulkRequestBuilder.operations(op -> op.update(updateOperation));

    return this;
  }

  @Override
  public BatchRequest update(final String index, final String id, final OperateEntity entity)
      throws PersistenceException {
    LOGGER.debug("Add update request for index {} id {} and entity {}", index, id, entity);

    final var updateOperation =
        new UpdateOperation.Builder<OperateEntity, OperateEntity>()
            .index(index)
            .id(id)
            .action(a -> a.doc(entity))
            .retryOnConflict(ElasticsearchUtil.UPDATE_RETRY_COUNT)
            .build();
    bulkRequestBuilder.operations(op -> op.update(updateOperation));

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

    final var updateOperation =
        new UpdateOperation.Builder<OperateEntity, Map<String, Object>>()
            .index(index)
            .id(id)
            .action(a -> a.script(esScriptBuilder.getScriptWithParameters(script, parameters)))
            .retryOnConflict(ElasticsearchUtil.UPDATE_RETRY_COUNT)
            .build();
    bulkRequestBuilder.operations(op -> op.update(updateOperation));

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
              String.format(
                  "Bulk request execution failed. %s. Cause: %s.", item, item.error().reason()));
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

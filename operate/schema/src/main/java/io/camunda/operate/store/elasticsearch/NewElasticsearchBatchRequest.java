/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch request that uses new style ElasticsearchClient instead of deprecated RestHighLevelClient.
 */
public class NewElasticsearchBatchRequest implements BatchRequest {

  public static final String DEFAULT_SCRIPT_LANG = "painless";

  private static final Logger LOGGER = LoggerFactory.getLogger(NewElasticsearchBatchRequest.class);

  private final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
  private final ElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public NewElasticsearchBatchRequest(final ElasticsearchClient esClient) {
    this(esClient, new ObjectMapper());
  }

  public NewElasticsearchBatchRequest(
      final ElasticsearchClient esClient, ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
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
            .retryOnConflict(UPDATE_RETRY_COUNT)
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
            .retryOnConflict(UPDATE_RETRY_COUNT)
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
            .action(a -> a.script(getScriptWithParameters(script, parameters)).upsert(entity))
            .retryOnConflict(UPDATE_RETRY_COUNT)
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
            .action(a -> a.script(getScriptWithParameters(script, parameters)).upsert(entity))
            .retryOnConflict(UPDATE_RETRY_COUNT)
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
            .retryOnConflict(UPDATE_RETRY_COUNT)
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
            .retryOnConflict(UPDATE_RETRY_COUNT)
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
            .action(a -> a.script(getScriptWithParameters(script, parameters)))
            .retryOnConflict(UPDATE_RETRY_COUNT)
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

  private static Script getScriptWithParameters(
      final String script, final Map<String, Object> parameters) {
    return new Script.Builder()
        .inline(b -> b.source(script).params(jsonParams(parameters)).lang(DEFAULT_SCRIPT_LANG))
        .build();
  }

  private static Map<String, JsonData> jsonParams(final Map<String, Object> params) {
    return params.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> json(e.getValue())));
  }

  private static <V> JsonData json(final V value) {
    return JsonData.of(value == null ? JsonValue.NULL : value);
  }
}

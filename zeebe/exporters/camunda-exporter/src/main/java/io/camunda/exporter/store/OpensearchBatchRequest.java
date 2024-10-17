/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.exporter.exceptions.OpensearchExporterException;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.utils.OpensearchScriptBuilder;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class OpensearchBatchRequest implements BatchRequest {
  public static final int UPDATE_RETRY_COUNT = 3;

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchBatchRequest.class);
  private final OpenSearchClient osClient;
  private final BulkRequest.Builder bulkRequestBuilder;
  private final OpensearchScriptBuilder scriptBuilder;

  public OpensearchBatchRequest(
      final OpenSearchClient osClient,
      final BulkRequest.Builder bulkRequestBuilder,
      final OpensearchScriptBuilder scriptBuilder) {
    this.osClient = osClient;
    this.bulkRequestBuilder = bulkRequestBuilder;
    this.scriptBuilder = scriptBuilder;
  }

  @Override
  public BatchRequest add(final String index, final ExporterEntity entity) {
    return addWithId(index, entity.getId(), entity);
  }

  @Override
  public BatchRequest addWithId(final String index, final String id, final ExporterEntity entity) {
    LOGGER.debug("Add index request for index {} id {} and entity {} ", index, id, entity);
    bulkRequestBuilder.operations(op -> op.index(idx -> idx.index(index).id(id).document(entity)));
    return this;
  }

  @Override
  public BatchRequest addWithRouting(
      final String index, final ExporterEntity entity, final String routing) {
    LOGGER.debug(
        "Add index request with routing {} for index {} and entity {} ", routing, index, entity);
    bulkRequestBuilder.operations(
        op ->
            op.index(idx -> idx.index(index).id(entity.getId()).document(entity).routing(routing)));

    return this;
  }

  @Override
  public BatchRequest upsert(
      final String index,
      final String id,
      final ExporterEntity entity,
      final Map<String, Object> updateFields) {
    return upsertWithRouting(index, id, entity, updateFields, null);
  }

  @Override
  public BatchRequest upsertWithRouting(
      final String index,
      final String id,
      final ExporterEntity entity,
      final Map<String, Object> updateFields,
      final String routing) {
    LOGGER.debug(
        "Add upsert request with routing {} for index {} id {} entity {} and update fields {}",
        routing,
        index,
        id,
        entity,
        updateFields);

    bulkRequestBuilder.operations(
        op ->
            op.update(
                upd ->
                    upd.index(index)
                        .id(id)
                        .upsert(entity)
                        .document(updateFields)
                        .routing(routing)));

    return this;
  }

  @Override
  public BatchRequest upsertWithScript(
      final String index,
      final String id,
      final ExporterEntity entity,
      final String script,
      final Map<String, Object> parameters) {
    return upsertWithScriptAndRouting(index, id, entity, script, parameters, null);
  }

  @Override
  public BatchRequest upsertWithScriptAndRouting(
      final String index,
      final String id,
      final ExporterEntity entity,
      final String script,
      final Map<String, Object> parameters,
      final String routing) {
    LOGGER.debug(
        "Add upsert request with routing {} for index {} id {} entity {} and script {} with parameters {} ",
        routing,
        index,
        id,
        entity,
        script,
        parameters);

    bulkRequestBuilder.operations(
        op ->
            op.update(
                upd ->
                    upd.index(index)
                        .id(id)
                        .upsert(entity)
                        .script(scriptBuilder.getScriptWithParameters(script, parameters))
                        .routing(routing)
                        .retryOnConflict(UPDATE_RETRY_COUNT)));

    return this;
  }

  @Override
  public BatchRequest update(
      final String index, final String id, final Map<String, Object> updateFields) {
    LOGGER.debug(
        "Add update request for index {} id {} and update fields {}", index, id, updateFields);

    bulkRequestBuilder.operations(
        op ->
            op.update(
                upd ->
                    upd.index(index)
                        .id(id)
                        .document(updateFields)
                        .retryOnConflict(UPDATE_RETRY_COUNT)));

    return this;
  }

  @Override
  public BatchRequest update(final String index, final String id, final ExporterEntity entity) {
    LOGGER.debug("Add update request for index {} id {} and entity {}", index, id, entity);

    bulkRequestBuilder.operations(
        op ->
            op.update(
                upd ->
                    upd.index(index).id(id).document(entity).retryOnConflict(UPDATE_RETRY_COUNT)));

    return this;
  }

  @Override
  public BatchRequest updateWithScript(
      final String index,
      final String id,
      final String script,
      final Map<String, Object> parameters) {
    LOGGER.debug(
        "Add upsert request with for index {} id {} and script {} with parameters {} ",
        index,
        id,
        script,
        parameters);

    bulkRequestBuilder.operations(
        op ->
            op.update(
                upd ->
                    upd.index(index)
                        .id(id)
                        .script(scriptBuilder.getScriptWithParameters(script, parameters))
                        .retryOnConflict(UPDATE_RETRY_COUNT)));

    return this;
  }

  @Override
  public BatchRequest delete(final String index, final String id) {
    LOGGER.debug("Add delete request for index {} and id {}", index, id);
    bulkRequestBuilder.operations(op -> op.delete(del -> del.index(index).id(id)));
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
    processBulkRequest(bulkRequest);
  }

  private void processBulkRequest(final BulkRequest bulkRequest) throws PersistenceException {
    if (bulkRequest.operations().isEmpty()) {
      return;
    }
    try {
      final BulkResponse bulkItemResponses = osClient.bulk(bulkRequest);
      final List<BulkResponseItem> items = bulkItemResponses.items();
      for (final BulkResponseItem responseItem : items) {
        if (responseItem.error() != null) {
          LOGGER.warn(
              String.format(
                  "%s failed for type [%s] and id [%s]: %s",
                  responseItem.operationType(),
                  responseItem.index(),
                  responseItem.id(),
                  responseItem.error().reason()),
              "error on OpenSearch BulkRequest");
          throw new PersistenceException(
              "Operation failed: " + responseItem.error().reason(),
              new OpensearchExporterException(responseItem.error().reason()),
              Integer.valueOf(responseItem.id()));
        }
      }
    } catch (final IOException | OpenSearchException ex) {
      throw new PersistenceException(
          "Error when processing bulk request against OpenSearch: " + ex.getMessage(), ex);
    }
  }
}

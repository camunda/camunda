/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkRequest.Builder;
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

  public OpensearchBatchRequest(final OpenSearchClient osClient, final Builder bulkRequestBuilder) {
    this.osClient = osClient;
    this.bulkRequestBuilder = bulkRequestBuilder;
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
  public BatchRequest delete(final String index, final String id) {
    LOGGER.debug("Add delete request for index {} and id {}", index, id);
    bulkRequestBuilder.operations(op -> op.delete(del -> del.index(index).id(id)));
    return this;
  }

  @Override
  public BatchRequest deleteWithRouting(final String index, final String id, final String routing) {
    LOGGER.debug(
        "Add delete index request with routing {} for index {} and entity {} ", routing, index, id);
    bulkRequestBuilder.operations(op -> op.delete(idx -> idx.index(index).id(id).routing(routing)));
    return this;
  }

  @Override
  public void execute(final BiConsumer<String, Error> customErrorHandlers)
      throws PersistenceException {
    execute(customErrorHandlers, false);
  }

  @Override
  public void executeWithRefresh() throws PersistenceException {
    execute(null, true);
  }

  private void execute(
      final BiConsumer<String, Error> customErrorHandlers, final boolean shouldRefresh)
      throws PersistenceException {
    if (shouldRefresh) {
      bulkRequestBuilder.refresh(Refresh.True);
    }
    final BulkRequest bulkRequest = bulkRequestBuilder.build();
    processBulkRequest(bulkRequest, customErrorHandlers);
  }

  private void processBulkRequest(
      final BulkRequest bulkRequest, final BiConsumer<String, Error> customErrorHandlers)
      throws PersistenceException {
    if (bulkRequest.operations().isEmpty()) {
      return;
    }
    try {
      final BulkResponse bulkItemResponses = osClient.bulk(bulkRequest);
      final List<BulkResponseItem> items = bulkItemResponses.items();
      validateNoErrors(items, customErrorHandlers);
    } catch (final IOException | OpenSearchException ex) {
      throw new PersistenceException(
          "Error when processing bulk request against OpenSearch: " + ex.getMessage(), ex);
    }
  }

  private void validateNoErrors(
      final List<BulkResponseItem> items, final BiConsumer<String, Error> errorHandlers) {
    final var errorItems = items.stream().filter(item -> item.error() != null).toList();
    if (errorItems.isEmpty()) {
      return;
    }

    final String errorMessages =
        errorItems.stream()
            .map(
                item ->
                    String.format(
                        "%s failed for type [%s] and id [%s]: %s",
                        item.operationType(), item.index(), item.id(), item.error().reason()))
            .collect(Collectors.joining(", \n"));
    LOGGER.warn("Bulk request execution failed: \n[{}]", errorMessages);

    errorItems.forEach(
        item -> {
          final String message =
              String.format(
                  "%s failed for type [%s] and id [%s]: %s",
                  item.operationType(), item.index(), item.id(), item.error().reason());
          if (errorHandlers != null) {
            final Error error = new Error(message, item.error().type(), item.status());
            errorHandlers.accept(item.index(), error);
          } else {
            throw new PersistenceException(message);
          }
        });
  }
}

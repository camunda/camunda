/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import co.elastic.clients.elasticsearch.core.bulk.UpdateAction;
import co.elastic.clients.elasticsearch.core.bulk.UpdateAction.Builder;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.util.BinaryData;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import io.camunda.exporter.utils.NdJsonSizeUtil;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class ElasticsearchBatchRequest implements BatchRequest {
  public static final int UPDATE_RETRY_COUNT = 3;
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchBatchRequest.class);
  private final ElasticsearchClient esClient;
  private final ElasticsearchScriptBuilder scriptBuilder;
  private final JsonpMapper jsonpMapper;
  private final List<SizedOperation> operations = new ArrayList<>();
  private long maxBulkBytes;
  private CamundaExporterMetrics metrics;

  public ElasticsearchBatchRequest(
      final ElasticsearchClient esClient, final ElasticsearchScriptBuilder scriptBuilder) {
    this.esClient = esClient;
    this.scriptBuilder = scriptBuilder;
    jsonpMapper = esClient._jsonpMapper();
  }

  @Override
  public BatchRequest withMetrics(final CamundaExporterMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  @Override
  public BatchRequest withMaxBytes(final long maxBulkBytes) {
    this.maxBulkBytes = maxBulkBytes;
    return this;
  }

  @Override
  public BatchRequest add(final String index, final ExporterEntity entity) {
    return addWithId(index, entity.getId(), entity);
  }

  @Override
  public BatchRequest addWithId(final String index, final String id, final ExporterEntity entity) {
    LOGGER.debug("Add index request for index {} id {} and entity {} ", index, id, entity);
    addIndexOp(index, id, null, entity);
    return this;
  }

  @Override
  public BatchRequest addWithRouting(
      final String index, final ExporterEntity entity, final String routing) {
    LOGGER.debug(
        "Add index request with routing {} for index {} and entity {} ", routing, index, entity);
    addIndexOp(index, entity.getId(), routing, entity);
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
    addUpdateOp(index, id, routing, a -> a.doc(updateFields).upsert(entity));
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
    addUpdateOp(
        index,
        id,
        routing,
        a -> a.script(scriptBuilder.getScriptWithParameters(script, parameters)).upsert(entity));
    return this;
  }

  @Override
  public BatchRequest update(
      final String index, final String id, final Map<String, Object> updateFields) {
    LOGGER.debug(
        "Add update request for index {} id {} and update fields {}", index, id, updateFields);
    addUpdateOp(index, id, null, a -> a.doc(updateFields));
    return this;
  }

  @Override
  public BatchRequest update(final String index, final String id, final ExporterEntity entity) {
    LOGGER.debug("Add update request for index {} id {} and entity {}", index, id, entity);
    addUpdateOp(index, id, null, a -> a.doc(entity));
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
    addUpdateOp(
        index, id, null, a -> a.script(scriptBuilder.getScriptWithParameters(script, parameters)));
    return this;
  }

  @Override
  public BatchRequest delete(final String index, final String id) {
    LOGGER.debug("Add delete request for index {} and id {}", index, id);
    addDeleteOp(index, id, null);
    return this;
  }

  @Override
  public BatchRequest deleteWithRouting(final String index, final String id, final String routing) {
    LOGGER.debug(
        "Add delete index request with routing {} for index {} and entity {} ", routing, index, id);
    addDeleteOp(index, id, routing);
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

  private void addIndexOp(
      final String index, final String id, final String routing, final ExporterEntity entity) {
    final BinaryData binaryDoc = BinaryData.of(entity, jsonpMapper);
    final IndexOperation<Object> indexOp =
        IndexOperation.of(i -> i.index(index).id(id).routing(routing).document(binaryDoc));
    addOperation(BulkOperation.of(b -> b.index(indexOp)), binaryDoc.size());
  }

  private void addUpdateOp(
      final String index,
      final String id,
      final String routing,
      final Function<Builder<Object, Object>, ?> actionBuilder) {
    final UpdateAction<Object, Object> action =
        UpdateAction.of(
            b -> {
              actionBuilder.apply(b);
              return b;
            });
    final BinaryData binaryAction = BinaryData.of(action, jsonpMapper);
    final BulkOperation op =
        BulkOperation.of(
            b ->
                b.update(
                    u ->
                        u.index(index)
                            .id(id)
                            .routing(routing)
                            .retryOnConflict(UPDATE_RETRY_COUNT)
                            .binaryAction(binaryAction)));
    addOperation(op, binaryAction.size());
  }

  private void addDeleteOp(final String index, final String id, final String routing) {
    final BulkOperation op =
        BulkOperation.of(b -> b.delete(d -> d.index(index).id(id).routing(routing)));
    addOperation(op, 0L);
  }

  private void addOperation(final BulkOperation op, final long payloadBytes) {
    operations.add(new SizedOperation(op, payloadBytes));
  }

  private void execute(
      final BiConsumer<String, Error> customErrorHandlers, final boolean shouldRefresh)
      throws PersistenceException {
    if (operations.isEmpty()) {
      return;
    }
    try {
      for (final List<BulkOperation> chunk : chunkByBytes(operations, maxBulkBytes)) {
        executeChunk(chunk, shouldRefresh, customErrorHandlers);
      }
    } finally {
      operations.clear();
    }
  }

  static List<List<BulkOperation>> chunkByBytes(
      final List<SizedOperation> ops, final long maxBytes) {
    final List<List<BulkOperation>> chunks = new ArrayList<>();
    List<BulkOperation> current = new ArrayList<>();
    long currentBytes = 0L;
    for (final SizedOperation sized : ops) {
      if (!current.isEmpty() && currentBytes + sized.sizeBytes() > maxBytes) {
        chunks.add(current);
        current = new ArrayList<>();
        currentBytes = 0L;
      }
      current.add(sized.operation());
      currentBytes += sized.sizeBytes();
    }
    if (!current.isEmpty()) {
      chunks.add(current);
    }
    return chunks;
  }

  private void executeChunk(
      final List<BulkOperation> chunkOps,
      final boolean shouldRefresh,
      final BiConsumer<String, Error> customErrorHandlers)
      throws PersistenceException {
    if (chunkOps.isEmpty()) {
      return;
    }
    final BulkRequest.Builder builder = new BulkRequest.Builder().operations(chunkOps);
    if (shouldRefresh) {
      builder.refresh(Refresh.True);
    }
    final BulkRequest bulkRequest = builder.build();
    try {
      final BulkResponse bulkResponse = esClient.bulk(bulkRequest);
      final List<BulkResponseItem> items = bulkResponse.items();
      validateNoErrors(items, customErrorHandlers);
      if (metrics != null) {
        metrics.recordBulkOperations(bulkRequest.operations().size());
      }
    } catch (final IOException | ElasticsearchException ex) {
      if (isRequestEntityTooLarge(ex)) {
        LOGGER.error("The entities in the payload to ES are too large, cannot write batch", ex);
        logBulkFailureTrace(bulkRequest, ex);
      }
      throw new PersistenceException(
          "Error when processing bulk request against Elasticsearch: " + ex.getMessage(), ex);
    }
  }

  private void validateNoErrors(
      final List<BulkResponseItem> items, final BiConsumer<String, Error> customErrorHandlers) {
    final var errorItems = items.stream().filter(item -> item.error() != null).toList();
    if (errorItems.isEmpty()) {
      return;
    }

    final Map<ErrorKey, ErrorValues> groupedErrors =
        errorItems.stream()
            .collect(
                Collectors.groupingBy(
                    item -> new ErrorKey(item.operationType(), item.error().reason()),
                    LinkedHashMap::new,
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        list ->
                            new ErrorValues(
                                list.stream().map(BulkResponseItem::index).distinct().toList(),
                                list.stream().map(BulkResponseItem::id).toList()))));

    final String errorMessages =
        groupedErrors.entrySet().stream()
            .limit(50)
            .map(
                entry ->
                    String.format(
                        "%s failed on indexes [%s] with ids: [%s]: %s",
                        entry.getKey().operationType,
                        entry.getValue().indexes,
                        entry.getValue().ids,
                        entry.getKey().errorReason))
            .collect(Collectors.joining(", \n"));

    LOGGER.debug("Bulk request execution failed: \n[{}]", errorMessages);

    errorItems.forEach(
        item -> {
          final String message =
              String.format(
                  "%s failed on index [%s] and id [%s]: %s",
                  item.operationType(), item.index(), item.id(), item.error().reason());

          if (metrics != null) {
            metrics.recordFlushFailureType(item.error().type());
          }
          if (customErrorHandlers != null) {
            final Error error = new Error(message, item.error().type(), item.status());
            customErrorHandlers.accept(item.index(), error);
          } else {
            throw new PersistenceException(message);
          }
        });
  }

  private static boolean isRequestEntityTooLarge(final Exception ex) {
    final String message = ex.getMessage();
    return message != null && message.contains("413");
  }

  private void logBulkFailureTrace(final BulkRequest bulkRequest, final Exception ex) {
    if (!LOGGER.isTraceEnabled()) {
      return;
    }
    try {
      final var payloadSize =
          NdJsonSizeUtil.measureNdJsonPayloadSize(bulkRequest, esClient._jsonpMapper());
      LOGGER.trace(
          "Elasticsearch bulk request FAILED: operations={} serializedPayloadBytes={} error={}",
          bulkRequest.operations().size(),
          payloadSize.totalBytes(),
          ex.getMessage());
      LOGGER.trace("Breakdown of operations in bulk request: {}", payloadSize.operationSizes());
    } catch (final Exception measureEx) {
      LOGGER.trace(
          "Elasticsearch bulk request FAILED and payload measurement also failed: "
              + "operations={} error={} measurementError={}",
          bulkRequest.operations().size(),
          ex.getMessage(),
          measureEx.getMessage());
    }
  }

  record SizedOperation(BulkOperation operation, long sizeBytes) {}

  private record ErrorValues(List<String> indexes, List<String> ids) {}

  private record ErrorKey(OperationType operationType, String errorReason) {}
}

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
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.utils.OpensearchNdJsonSizeUtil;
import io.camunda.exporter.utils.OpensearchScriptBuilder;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.bulk.OperationType;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.util.BinaryData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class OpensearchBatchRequest implements BatchRequest {
  public static final int UPDATE_RETRY_COUNT = 3;
  private static final long DEFAULT_MAX_BULK_BYTES = 20L * 1024 * 1024;
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchBatchRequest.class);
  private final OpenSearchClient osClient;
  private final OpensearchScriptBuilder scriptBuilder;
  private final JsonpMapper jsonpMapper;
  private final List<SizedOperation> operations = new ArrayList<>();
  private long maxBulkBytes = DEFAULT_MAX_BULK_BYTES;
  private CamundaExporterMetrics metrics;

  public OpensearchBatchRequest(
      final OpenSearchClient osClient, final OpensearchScriptBuilder scriptBuilder) {
    this.osClient = osClient;
    this.scriptBuilder = scriptBuilder;
    jsonpMapper = osClient._transport().jsonpMapper();
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
    addUpdateOp(index, id, routing, b -> b.upsert(entity).document(updateFields));
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
        b -> b.upsert(entity).script(scriptBuilder.getScriptWithParameters(script, parameters)));
    return this;
  }

  @Override
  public BatchRequest update(
      final String index, final String id, final Map<String, Object> updateFields) {
    LOGGER.debug(
        "Add update request for index {} id {} and update fields {}", index, id, updateFields);
    addUpdateOp(index, id, null, b -> b.document(updateFields));
    return this;
  }

  @Override
  public BatchRequest update(final String index, final String id, final ExporterEntity entity) {
    LOGGER.debug("Add update request for index {} id {} and entity {}", index, id, entity);
    addUpdateOp(index, id, null, b -> b.document(entity));
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
        index, id, null, b -> b.script(scriptBuilder.getScriptWithParameters(script, parameters)));
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
    final IndexOperation<BinaryData> indexOp =
        IndexOperation.of(i -> i.index(index).id(id).routing(routing).document(binaryDoc));
    addOperation(BulkOperation.of(b -> b.index(indexOp)), binaryDoc.size());
  }

  private void addUpdateOp(
      final String index,
      final String id,
      final String routing,
      final Function<UpdateOperation.Builder<Object>, ?> opBuilder) {
    final UpdateOperation<Object> updateOp =
        UpdateOperation.of(
            b -> {
              b.index(index).id(id).routing(routing).retryOnConflict(UPDATE_RETRY_COUNT);
              opBuilder.apply(b);
              return b;
            });
    final long sizeBytes =
        OpensearchNdJsonSizeUtil.measureSingleNdJsonSerializable(updateOp, jsonpMapper);
    addOperation(BulkOperation.of(b -> b.update(updateOp)), sizeBytes);
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
    for (final List<BulkOperation> chunk : chunkByBytes(operations, maxBulkBytes)) {
      executeChunk(chunk, shouldRefresh, customErrorHandlers);
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
      final BulkResponse bulkItemResponses = osClient.bulk(bulkRequest);
      final List<BulkResponseItem> items = bulkItemResponses.items();
      validateNoErrors(items, customErrorHandlers);
      if (metrics != null) {
        metrics.recordBulkOperations(bulkRequest.operations().size());
      }
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
                  "%s failed for type [%s] and id [%s]: %s",
                  item.operationType(), item.index(), item.id(), item.error().reason());

          if (metrics != null) {
            metrics.recordFlushFailureType(item.error().type());
          }
          if (errorHandlers != null) {
            final Error error = new Error(message, item.error().type(), item.status());
            errorHandlers.accept(item.index(), error);
          } else {
            throw new PersistenceException(message);
          }
        });
  }

  record SizedOperation(BulkOperation operation, long sizeBytes) {}

  private record ErrorValues(List<String> indexes, List<String> ids) {}

  private record ErrorKey(OperationType operationType, String errorReason) {}
}

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
import co.elastic.clients.elasticsearch.core.BulkRequest.Builder;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.MurmurHash3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class ElasticsearchBatchRequest implements BatchRequest {
  public static final int UPDATE_RETRY_COUNT = 3;

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchBatchRequest.class);
  private final ElasticsearchClient esClient;
  private final Map<IndexAndShard, BulkRequest.Builder> bulkRequestBuilders = new HashMap<>();
  private final Map<String, Integer> indexes = new HashMap<>();
  private final ElasticsearchScriptBuilder scriptBuilder;
  private CamundaExporterMetrics metrics;

  public ElasticsearchBatchRequest(
      final ElasticsearchClient esClient,
      final BulkRequest.Builder bulkRequestBuilder,
      final ElasticsearchScriptBuilder scriptBuilder) {
    this.esClient = esClient;
    // this.bulkRequestBuilder = bulkRequestBuilder;
    this.scriptBuilder = scriptBuilder;
  }

  private BulkRequest.Builder builderForIndex(
      final String index, final String id, final String routing) {
    // System.out.println(index);
    final var indexId = routing != null ? routing : id;
    indexes.compute(index, (i, count) -> count == null ? 1 : count + 1);

    if (false) {
      return bulkRequestBuilders.computeIfAbsent(
          new IndexAndShard("singleIndex", 0), idx -> new BulkRequest.Builder());
    }

    final int shards = 5;
    var indexKey = new IndexAndShard("default", -1);
    if (index.startsWith("operate-list-view-")) {
      indexKey =
          new IndexAndShard(
              "operate-list-view",
              MurmurHash3.hash32x86(indexId.getBytes(StandardCharsets.UTF_8)) % shards);
    } else if (index.startsWith("operate-flownode-instance")) {
      indexKey =
          new IndexAndShard(
              "operate-flownode-instance",
              MurmurHash3.hash32x86(indexId.getBytes(StandardCharsets.UTF_8)) % shards);
    } else if (index.startsWith("operate-sequence-flow")) {
      indexKey =
          new IndexAndShard(
              "operate-sequence-flow",
              MurmurHash3.hash32x86(indexId.getBytes(StandardCharsets.UTF_8)) % shards);
    }

    return bulkRequestBuilders.computeIfAbsent(indexKey, idx -> new BulkRequest.Builder());
  }

  @Override
  public BatchRequest withMetrics(final CamundaExporterMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  @Override
  public BatchRequest add(final String index, final ExporterEntity entity) {
    return addWithId(index, entity.getId(), entity);
  }

  @Override
  public BatchRequest addWithId(final String index, final String id, final ExporterEntity entity) {
    LOGGER.debug("Add index request for index {} id {} and entity {} ", index, id, entity);
    builderForIndex(index, id, id)
        .operations(op -> op.index(idx -> idx.index(index).id(id).document(entity)));
    return this;
  }

  @Override
  public BatchRequest addWithRouting(
      final String index, final ExporterEntity entity, final String routing) {
    LOGGER.debug(
        "Add index request with routing {} for index {} and entity {} ", routing, index, entity);
    builderForIndex(index, entity.getId(), routing)
        .operations(
            op ->
                op.index(
                    idx -> idx.index(index).id(entity.getId()).document(entity).routing(routing)));

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

    builderForIndex(index, id, routing)
        .operations(
            op ->
                op.update(
                    upd ->
                        upd.index(index)
                            .id(id)
                            .routing(routing)
                            .action(a -> a.doc(updateFields).upsert(entity))
                            .retryOnConflict(UPDATE_RETRY_COUNT)));

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

    builderForIndex(index, id, routing)
        .operations(
            op ->
                op.update(
                    upd ->
                        upd.index(index)
                            .id(id)
                            .routing(routing)
                            .action(
                                a ->
                                    a.script(
                                            scriptBuilder.getScriptWithParameters(
                                                script, parameters))
                                        .upsert(entity))
                            .retryOnConflict(UPDATE_RETRY_COUNT)));

    return this;
  }

  @Override
  public BatchRequest update(
      final String index, final String id, final Map<String, Object> updateFields) {
    LOGGER.debug(
        "Add update request for index {} id {} and update fields {}", index, id, updateFields);

    builderForIndex(index, id, id)
        .operations(
            op ->
                op.update(
                    up ->
                        up.index(index)
                            .id(id)
                            .action(a -> a.doc(updateFields))
                            .retryOnConflict(UPDATE_RETRY_COUNT)));

    return this;
  }

  @Override
  public BatchRequest update(final String index, final String id, final ExporterEntity entity) {
    LOGGER.debug("Add update request for index {} id {} and entity {}", index, id, entity);

    builderForIndex(index, id, id)
        .operations(
            op ->
                op.update(
                    up ->
                        up.index(index)
                            .id(id)
                            .action(a -> a.doc(entity))
                            .retryOnConflict(UPDATE_RETRY_COUNT)));

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

    builderForIndex(index, id, id)
        .operations(
            op ->
                op.update(
                    up ->
                        up.index(index)
                            .id(id)
                            .action(
                                a ->
                                    a.script(
                                        scriptBuilder.getScriptWithParameters(script, parameters)))
                            .retryOnConflict(UPDATE_RETRY_COUNT)));

    return this;
  }

  @Override
  public BatchRequest delete(final String index, final String id) {
    LOGGER.debug("Add delete request for index {} and id {}", index, id);
    builderForIndex(index, id, id).operations(op -> op.delete(del -> del.index(index).id(id)));
    return this;
  }

  @Override
  public BatchRequest deleteWithRouting(final String index, final String id, final String routing) {
    LOGGER.debug(
        "Add delete index request with routing {} for index {} and entity {} ", routing, index, id);
    builderForIndex(index, id, routing)
        .operations(op -> op.delete(idx -> idx.index(index).id(id).routing(routing)));
    return this;
  }

  @Override
  public void execute(final BiConsumer<String, Error> customErrorHandlers)
      throws PersistenceException {
    // final long start = System.currentTimeMillis();
    execute(customErrorHandlers, false);
    /*final long end = System.currentTimeMillis();
    System.out.println("Bulk request executed in " + (end - start) + " ms");*/
  }

  @Override
  public void executeWithRefresh() throws PersistenceException {
    execute(null, true);
  }

  private void execute(
      final BiConsumer<String, Error> customErrorHandlers, final boolean shouldRefresh)
      throws PersistenceException {
    // System.out.println("Writing to Elasticsearch indexes: " + indexes);
    if (bulkRequestBuilders.size() == 1) {
      // System.out.println("Single writing");
      doBulkRequest(
          customErrorHandlers, shouldRefresh, bulkRequestBuilders.values().iterator().next());
      return;
    }
    // System.out.println("Multi writing");
    final var threads = new ArrayList<Thread>(bulkRequestBuilders.size());
    for (final var entry : bulkRequestBuilders.entrySet()) {
      final var bulkRequestBuilder = entry.getValue();
      final var t =
          Thread.ofVirtual()
              .start(
                  () -> {
                    // final long start = System.currentTimeMillis();
                    final var ops =
                        doBulkRequest(customErrorHandlers, shouldRefresh, bulkRequestBuilder);
                    /*final long end = System.currentTimeMillis();
                    System.out.println(
                        "Bulk request for index "
                            + entry.getKey()
                            + " executed in "
                            + (end - start)
                            + " ms and contained "
                            + ops);*/
                  });
      threads.add(t);
    }
    for (final var t : threads) {
      try {
        t.join();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
  }

  private int doBulkRequest(
      final BiConsumer<String, Error> customErrorHandlers,
      final boolean shouldRefresh,
      final Builder bulkRequestBuilder) {
    if (shouldRefresh) {
      bulkRequestBuilder.refresh(Refresh.True);
    }
    final BulkRequest bulkRequest = bulkRequestBuilder.build();
    if (bulkRequest.operations().isEmpty()) {
      return 0;
    }

    try {
      final BulkResponse bulkResponse = esClient.bulk(bulkRequest);
      final List<BulkResponseItem> items = bulkResponse.items();
      validateNoErrors(items, customErrorHandlers);
      if (metrics != null) {
        metrics.recordBulkOperations(bulkRequest.operations().size());
      }
    } catch (final IOException | ElasticsearchException ex) {
      throw new PersistenceException(
          "Error when processing bulk request against Elasticsearch: " + ex.getMessage(), ex);
    }

    final int ops = bulkRequest.operations().size();
    // System.out.println("Bulk request executed with " + ops + " operations");
    return ops;
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

  record IndexAndShard(String index, int shard) {}

  private record ErrorValues(List<String> indexes, List<String> ids) {}

  private record ErrorKey(OperationType operationType, String errorReason) {}
}

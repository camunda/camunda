/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.util.Map;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link BatchRequest} implementation that feeds operations to a {@link BulkIngester} instead of
 * executing them synchronously. The ingester manages batching, sizing, and async flushing.
 *
 * <p>{@link #execute} is a no-op — the ingester decides when to flush based on its own
 * size/count/interval thresholds.
 */
@SuppressWarnings("rawtypes")
public class IngesterBatchRequest implements BatchRequest {

  private static final int UPDATE_RETRY_COUNT = 3;
  private static final Logger LOGGER = LoggerFactory.getLogger(IngesterBatchRequest.class);

  private final BulkIngester<Void> ingester;
  private final ElasticsearchScriptBuilder scriptBuilder;
  private CamundaExporterMetrics metrics;
  private int operationCount;

  public IngesterBatchRequest(
      final BulkIngester<Void> ingester, final ElasticsearchScriptBuilder scriptBuilder) {
    this.ingester = ingester;
    this.scriptBuilder = scriptBuilder;
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
    ingester.add(
        BulkOperation.of(op -> op.index(idx -> idx.index(index).id(id).document(entity))));
    operationCount++;
    return this;
  }

  @Override
  public BatchRequest addWithRouting(
      final String index, final ExporterEntity entity, final String routing) {
    LOGGER.debug(
        "Add index request with routing {} for index {} and entity {} ", routing, index, entity);
    ingester.add(
        BulkOperation.of(
            op ->
                op.index(
                    idx ->
                        idx.index(index).id(entity.getId()).document(entity).routing(routing))));
    operationCount++;
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
    ingester.add(
        BulkOperation.of(
            op ->
                op.update(
                    upd ->
                        upd.index(index)
                            .id(id)
                            .routing(routing)
                            .action(a -> a.doc(updateFields).upsert(entity))
                            .retryOnConflict(UPDATE_RETRY_COUNT))));
    operationCount++;
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
        "Add upsert request with routing {} for index {} id {} entity {} and script {} with"
            + " parameters {} ",
        routing,
        index,
        id,
        entity,
        script,
        parameters);
    ingester.add(
        BulkOperation.of(
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
                            .retryOnConflict(UPDATE_RETRY_COUNT))));
    operationCount++;
    return this;
  }

  @Override
  public BatchRequest update(
      final String index, final String id, final Map<String, Object> updateFields) {
    LOGGER.debug(
        "Add update request for index {} id {} and update fields {}", index, id, updateFields);
    ingester.add(
        BulkOperation.of(
            op ->
                op.update(
                    up ->
                        up.index(index)
                            .id(id)
                            .action(a -> a.doc(updateFields))
                            .retryOnConflict(UPDATE_RETRY_COUNT))));
    operationCount++;
    return this;
  }

  @Override
  public BatchRequest update(final String index, final String id, final ExporterEntity entity) {
    LOGGER.debug("Add update request for index {} id {} and entity {}", index, id, entity);
    ingester.add(
        BulkOperation.of(
            op ->
                op.update(
                    up ->
                        up.index(index)
                            .id(id)
                            .action(a -> a.doc(entity))
                            .retryOnConflict(UPDATE_RETRY_COUNT))));
    operationCount++;
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
    ingester.add(
        BulkOperation.of(
            op ->
                op.update(
                    up ->
                        up.index(index)
                            .id(id)
                            .action(
                                a ->
                                    a.script(
                                        scriptBuilder.getScriptWithParameters(
                                            script, parameters)))
                            .retryOnConflict(UPDATE_RETRY_COUNT))));
    operationCount++;
    return this;
  }

  @Override
  public BatchRequest delete(final String index, final String id) {
    LOGGER.debug("Add delete request for index {} and id {}", index, id);
    ingester.add(BulkOperation.of(op -> op.delete(del -> del.index(index).id(id))));
    operationCount++;
    return this;
  }

  @Override
  public BatchRequest deleteWithRouting(
      final String index, final String id, final String routing) {
    LOGGER.debug(
        "Add delete index request with routing {} for index {} and entity {} ", routing, index, id);
    ingester.add(
        BulkOperation.of(op -> op.delete(idx -> idx.index(index).id(id).routing(routing))));
    operationCount++;
    return this;
  }

  @Override
  public void execute(final BiConsumer<String, Error> customErrorHandlers)
      throws PersistenceException {
    // No-op: the ingester manages flushing based on its own thresholds.
    // Operations were already added to the ingester in the operation methods above.
    if (metrics != null) {
      metrics.recordBulkOperations(operationCount);
    }
  }

  @Override
  public void executeWithRefresh() throws PersistenceException {
    // Force a flush for refresh scenarios (e.g. close)
    if (metrics != null) {
      metrics.recordBulkOperations(operationCount);
    }
    ingester.flush();
  }
}

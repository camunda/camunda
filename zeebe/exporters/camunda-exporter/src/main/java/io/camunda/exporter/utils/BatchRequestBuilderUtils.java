/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation.Builder;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateAction;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.util.ObjectBuilder;
import io.camunda.exporter.entities.ExporterEntity;
import io.camunda.exporter.store.ElasticsearchBatchRequest;
import java.util.Map;

public class BatchRequestBuilderUtils {

  public static ObjectBuilder<BulkOperation> bulkOperationBuilder(
      final IndexOperation<ExporterEntity> idx) {
    return new Builder().index(idx);
  }

  public static ObjectBuilder<BulkOperation> bulkOperationBuilder(
      final UpdateOperation<ExporterEntity, Map<String, Object>> updateOperation) {
    return new Builder().update(updateOperation);
  }

  public static UpdateOperation.Builder<ExporterEntity, Map<String, Object>> updateOperationBuilder(
      final String index,
      final String id,
      final UpdateAction<ExporterEntity, Map<String, Object>> upsert) {
    return new UpdateOperation.Builder<ExporterEntity, Map<String, Object>>()
        .index(index)
        .id(id)
        .action(upsert)
        .retryOnConflict(ElasticsearchBatchRequest.UPDATE_RETRY_COUNT);
  }

  public static UpdateAction.Builder<ExporterEntity, Map<String, Object>> updateActionBuilder() {
    return new UpdateAction.Builder<ExporterEntity, Map<String, Object>>();
  }

  public static UpdateAction.Builder<ExporterEntity, Map<String, Object>> upsertBuilder(
      final ExporterEntity entity) {
    return updateActionBuilder().upsert(entity);
  }

  public static UpdateAction.Builder<ExporterEntity, Map<String, Object>> upsertWithDocBuilder(
      final ExporterEntity entity, final Map<String, Object> updateFields) {
    return upsertBuilder(entity).doc(updateFields);
  }

  public static UpdateAction.Builder<ExporterEntity, Map<String, Object>> upsertWithScriptBuilder(
      final ExporterEntity entity, final Script script) {
    return upsertBuilder(entity).script(script);
  }

  public static IndexOperation.Builder<ExporterEntity> indexWithRoutingBuilder(
      final String index, final ExporterEntity entity, final String routing) {
    return indexBuilder(index, entity.getId(), entity).routing(routing);
  }

  public static IndexOperation.Builder<ExporterEntity> indexBuilder(
      final String index, final String id, final ExporterEntity entity) {
    return new IndexOperation.Builder<ExporterEntity>().index(index).id(id).document(entity);
  }
}

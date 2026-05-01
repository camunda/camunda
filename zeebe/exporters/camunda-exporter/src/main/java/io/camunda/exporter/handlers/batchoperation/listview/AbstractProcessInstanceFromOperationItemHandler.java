/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation.listview;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.batchoperation.AbstractOperationHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.IndexLocator;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import java.util.List;
import java.util.Map;

public abstract class AbstractProcessInstanceFromOperationItemHandler<
        R extends RecordValue & ProcessInstanceRelated>
    extends AbstractOperationHandler<ProcessInstanceForListViewEntity, R> {

  protected AbstractProcessInstanceFromOperationItemHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache,
      final OperationType relevantOperationType) {
    super(indexName, batchOperationCache, relevantOperationType);
  }

  @Override
  public Class<ProcessInstanceForListViewEntity> getEntityType() {
    return ProcessInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<R> record) {
    return record.getBatchOperationReference() != batchOperationReferenceNullValue()
        && record.getValue().getProcessInstanceKey() > 0
        && (isCompleted(record) || isFailed(record))
        && isRelevantForBatchOperation(record);
  }

  @Override
  public List<String> generateIds(final Record<R> record) {
    // Use a composite ID (processInstanceKey:batchOperationReference) so that each (PI, batchOp)
    // pair gets its own cached entity. This prevents a second batch operation targeting the same PI
    // from overwriting the first one's batchOperationId in the shared entity.
    return List.of(
        record.getValue().getProcessInstanceKey() + ":" + record.getBatchOperationReference());
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(final String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<R> record, final ProcessInstanceForListViewEntity entity) {
    entity.setBatchOperationIds(List.of(String.valueOf(record.getBatchOperationReference())));
  }

  @Override
  public void flush(
      final IndexLocator indexLocator,
      final ProcessInstanceForListViewEntity entity,
      final BatchRequest batchRequest)
      throws PersistenceException {
    // Extract just the processInstanceKey from the composite cache ID
    // (processInstanceKey:batchOperationReference).
    final String processInstanceKey = entity.getId().split(":")[0];
    final String script =
        "if (ctx._source.batchOperationIds == null){"
            + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
            + "} else if (!ctx._source.batchOperationIds.contains(params.batchOperationId)) {"
            + "ctx._source.batchOperationIds.add(params.batchOperationId);"
            + "}";
    batchRequest.updateWithScript(
        indexName,
        processInstanceKey,
        script,
        Map.of("batchOperationId", entity.getBatchOperationIds().getFirst()));
  }

  protected abstract boolean isFailed(final Record<R> record);

  protected abstract boolean isCompleted(final Record<R> record);
}

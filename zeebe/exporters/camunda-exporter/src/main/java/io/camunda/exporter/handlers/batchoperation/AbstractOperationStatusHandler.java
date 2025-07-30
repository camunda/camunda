/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.DateUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for handling the status of batch operation items. Subclasses of this class
 * should listen to zeebe records issued after a successful execution of a single item operation
 * command or when one of these commands is rejected. For each batch operation type one subclass
 * handler should exist overriding the <code>isCompleted()</code> or <code>isFailed()</code> method.
 *
 * @param <R> the type of the record value handled by this operation status handler
 */
public abstract class AbstractOperationStatusHandler<R extends RecordValue>
    extends AbstractOperationHandler<OperationEntity, R> {

  public static final String ERROR_MSG = "%s: %s";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(AbstractOperationStatusHandler.class);
  protected final ValueType handledValueType;

  public AbstractOperationStatusHandler(
      final String indexName,
      final ValueType handledValueType,
      final OperationType relevantOperationType,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(indexName, batchOperationCache, relevantOperationType);
    this.handledValueType = handledValueType;
  }

  @Override
  public ValueType getHandledValueType() {
    return handledValueType;
  }

  @Override
  public Class<OperationEntity> getEntityType() {
    return OperationEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<R> record) {
    return record.getBatchOperationReference() != batchOperationReferenceNullValue()
        && (isCompleted(record) || isFailed(record))
        && isRelevantForBatchOperation(record);
  }

  @Override
  public List<String> generateIds(final Record<R> record) {
    return List.of(generateId(record.getBatchOperationReference(), getItemKey(record)));
  }

  @Override
  public OperationEntity createNewEntity(final String id) {
    return new OperationEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<R> record, final OperationEntity entity) {
    entity
        .setBatchOperationId(String.valueOf(record.getBatchOperationReference()))
        .setItemKey(getItemKey(record))
        .setProcessInstanceKey(getProcessInstanceKey(record));

    if (isCompleted(record)) {
      entity.setState(OperationState.COMPLETED);
      entity.setCompletedDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
    } else if (isFailed(record)) {
      if (record.getRejectionType() == RejectionType.NOT_FOUND) {
        entity.setState(OperationState.SKIPPED);
      } else {
        entity.setState(OperationState.FAILED);
        entity.setErrorMessage(
            String.format(ERROR_MSG, record.getRejectionType(), record.getRejectionReason()));
      }
    }
  }

  @Override
  public void flush(final OperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(OperationTemplate.BATCH_OPERATION_ID, entity.getBatchOperationId());
    updateFields.put(OperationTemplate.PROCESS_INSTANCE_KEY, entity.getProcessInstanceKey());
    updateFields.put(OperationTemplate.ITEM_KEY, entity.getItemKey());
    updateFields.put(OperationTemplate.STATE, entity.getState());
    updateFields.put(OperationTemplate.COMPLETED_DATE, entity.getCompletedDate());
    updateFields.put(OperationTemplate.ERROR_MSG, entity.getErrorMessage());

    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
    LOGGER.trace("Updated operation {} with fields {}", entity.getId(), updateFields);
  }

  /**
   * Extract the operation itemKey from the record.
   *
   * @param record the record
   * @return the item key
   */
  abstract long getItemKey(Record<R> record);

  /**
   * Extract the process instance key from the record.
   *
   * @param record the record
   * @return the process instance key
   */
  abstract long getProcessInstanceKey(Record<R> record);

  /** Checks if the batch operation item is completed */
  abstract boolean isCompleted(Record<R> record);

  /** Checks if the batch operation item is failed */
  abstract boolean isFailed(Record<R> record);
}

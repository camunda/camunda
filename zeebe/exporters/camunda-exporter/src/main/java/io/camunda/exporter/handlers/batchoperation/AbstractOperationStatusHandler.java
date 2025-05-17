/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.operationReferenceNullValue;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.DateUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractOperationStatusHandler<R extends RecordValue>
    extends AbstractOperationHandler implements ExportHandler<OperationEntity, R> {
  public static final String ERROR_MSG = "%s: %s";

  protected final ValueType handledValueType;

  public AbstractOperationStatusHandler(final String indexName, final ValueType handledValueType) {
    super(indexName);
    this.handledValueType = handledValueType;
  }

  @Override
  public ValueType getHandledValueType() {
    return handledValueType;
  }

  @Override
  public boolean handlesRecord(final Record<R> record) {
    return record.getOperationReference() != operationReferenceNullValue()
        && (isCompleted(record) || isFailed(record));
  }

  @Override
  public List<String> generateIds(final Record<R> record) {
    return List.of(generateId(record.getOperationReference(), getItemKey(record)));
  }

  @Override
  public void updateEntity(final Record<R> record, final OperationEntity entity) {
    if (isCompleted(record)) {
      entity.setState(OperationState.COMPLETED);
      entity.setCompletedDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
    } else if (isFailed(record)) {
      entity.setState(OperationState.FAILED);
      entity.setErrorMessage(
          String.format(ERROR_MSG, record.getRejectionType(), record.getRejectionReason()));
    }
  }

  @Override
  public void flush(final OperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(OperationTemplate.STATE, entity.getState());
    updateFields.put(OperationTemplate.COMPLETED_DATE, entity.getCompletedDate());
    updateFields.put(OperationTemplate.ERROR_MSG, entity.getErrorMessage());

    batchRequest.update(indexName, entity.getId(), updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  abstract long getItemKey(Record<R> record);

  /** Checks if the batch operation item is completed */
  abstract boolean isCompleted(Record<R> record);

  /** Checks if the batch operation item is failed */
  abstract boolean isFailed(Record<R> record);
}

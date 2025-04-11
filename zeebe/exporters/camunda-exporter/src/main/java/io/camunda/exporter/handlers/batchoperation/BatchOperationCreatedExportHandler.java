/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.List;

public class BatchOperationCreatedExportHandler
    implements ExportHandler<BatchOperationEntity, BatchOperationCreationRecordValue> {

  private final String indexName;

  public BatchOperationCreatedExportHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.BATCH_OPERATION_CREATION;
  }

  @Override
  public Class<BatchOperationEntity> getEntityType() {
    return BatchOperationEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<BatchOperationCreationRecordValue> record) {
    return record.getIntent().equals(BatchOperationIntent.CREATED);
  }

  @Override
  public List<String> generateIds(final Record<BatchOperationCreationRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getBatchOperationKey()));
  }

  @Override
  public BatchOperationEntity createNewEntity(final String id) {
    return new BatchOperationEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<BatchOperationCreationRecordValue> record, final BatchOperationEntity entity) {
    final BatchOperationCreationRecordValue value = record.getValue();
    /*
    TODO
    The existing  BatchOperationEntity diverges a bit from the fields we expect from the zeebe batch operation records
    We have to make a decision if we should create a new entity or if we should adapt the existing one

    if we choose to adapt, we need to be sure that we have migrated all the data from the old entity to the new one
    - Migration can de done once in the migration process of brownfield upgrades
    - Or it can be done in on the read side, to adapt the results based on other field values

    Creating a new entity would mean that would mean that we have to complicate the query logic to be able to aggregate data from both indexes

    I'm more in favor of adapting the fields of the existing index
     */

    entity
        .setId(String.valueOf(value.getBatchOperationKey()))
        // FIXME this mapping is not 1:1 with the original value, we need to come back to it
        // TODO double check if we can change BatchOperationType value to map to OperationType
        .setType(OperationType.valueOf(value.getBatchOperationType().name()))
        // TODO check if we should move this to the STARTED
        // TODO it makes sense to also track the `CREATION` date
        .setStartDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
    // TODO set state
  }

  @Override
  public void flush(final BatchOperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

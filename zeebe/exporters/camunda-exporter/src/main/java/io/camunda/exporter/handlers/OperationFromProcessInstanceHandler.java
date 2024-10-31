/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_MIGRATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OperationFromProcessInstanceHandler
    implements ExportHandler<OperationEntity, ProcessInstanceRecordValue> {
  private static final Set<Intent> ELIGIBLE_STATES = Set.of(ELEMENT_MIGRATED, ELEMENT_TERMINATED);
  private final String indexName;

  public OperationFromProcessInstanceHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<OperationEntity> getEntityType() {
    return OperationEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    return isProcessEvent(record.getValue()) && ELIGIBLE_STATES.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    final String operationReference = String.valueOf(record.getOperationReference());
    return List.of(operationReference);
  }

  @Override
  public OperationEntity createNewEntity(final String id) {
    return new OperationEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record, final OperationEntity entity) {
    entity
        .setState(OperationState.COMPLETED)
        .setLockOwner(null)
        .setLockExpirationTime(null)
        .setCompletedDate(OffsetDateTime.now());
  }

  @Override
  public void flush(final OperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> updateFields =
        Map.of(
            OperationTemplate.STATE,
            entity.getState(),
            OperationTemplate.COMPLETED_DATE,
            entity.getCompletedDate(),
            OperationTemplate.LOCK_OWNER,
            entity.getLockOwner(),
            OperationTemplate.LOCK_EXPIRATION_TIME,
            entity.getLockExpirationTime());

    batchRequest.update(indexName, entity.getId(), updateFields);
  }

  private boolean isProcessEvent(final ProcessInstanceRecordValue recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(
      final ProcessInstanceRecordValue recordValue, final BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.WaitingStateEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.List;

public class WaitingStateHandler implements ExportHandler<WaitingStateEntity, JobRecordValue> {

  private static final String ELEMENT_TYPE_JOB = "JOB";
  private final String indexName;

  public WaitingStateHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.JOB;
  }

  @Override
  public Class<WaitingStateEntity> getEntityType() {
    return WaitingStateEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<JobRecordValue> record) {
    return record.getIntent() == JobIntent.CREATED;
  }

  @Override
  public List<String> generateIds(final Record<JobRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getElementInstanceKey()));
  }

  @Override
  public WaitingStateEntity createNewEntity(final String id) {
    return new WaitingStateEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<JobRecordValue> record, final WaitingStateEntity entity) {
    final JobRecordValue recordValue = record.getValue();

    entity
        .setElementInstanceKey(recordValue.getElementInstanceKey())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setElementType(ELEMENT_TYPE_JOB)
        .setDetails(recordValue.getType())
        .setTenantId(recordValue.getTenantId())
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition());
  }

  @Override
  public void flush(final WaitingStateEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

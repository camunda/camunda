/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.ordinal.OrdinalEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.OrdinalIntent;
import io.camunda.zeebe.protocol.record.value.OrdinalRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.Collections;
import java.util.List;

/**
 * Exports {@link OrdinalIntent#TICKED} records to the ordinal index. Each document captures the
 * ordinal value and the wall-clock date/time at which the tick occurred.
 */
public record OrdinalTickedHandler(String indexName)
    implements ExportHandler<OrdinalEntity, OrdinalRecordValue> {

  @Override
  public ValueType getHandledValueType() {
    return ValueType.ORDINAL;
  }

  @Override
  public Class<OrdinalEntity> getEntityType() {
    return OrdinalEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<OrdinalRecordValue> record) {
    return OrdinalIntent.TICKED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<OrdinalRecordValue> record) {
    return Collections.singletonList(String.valueOf(record.getKey()));
  }

  @Override
  public OrdinalEntity createNewEntity(final String id) {
    return new OrdinalEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<OrdinalRecordValue> record, final OrdinalEntity entity) {
    final var value = record.getValue();
    entity
        .setOrdinal(value.getOrdinal())
        .setDateTime(DateUtil.toOffsetDateTime(value.getDateTime()))
        .setPartitionId(record.getPartitionId());
  }

  @Override
  public void flush(final OrdinalEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

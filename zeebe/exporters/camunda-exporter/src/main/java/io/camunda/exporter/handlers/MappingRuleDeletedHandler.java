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
import io.camunda.webapps.schema.entities.usermanagement.MappingRuleEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import java.util.List;

public class MappingRuleDeletedHandler
    implements ExportHandler<MappingRuleEntity, MappingRuleRecordValue> {
  private final String indexName;

  public MappingRuleDeletedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.MAPPING_RULE;
  }

  @Override
  public Class<MappingRuleEntity> getEntityType() {
    return MappingRuleEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<MappingRuleRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && MappingRuleIntent.DELETED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<MappingRuleRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getMappingRuleId()));
  }

  @Override
  public MappingRuleEntity createNewEntity(final String id) {
    return new MappingRuleEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<MappingRuleRecordValue> record, final MappingRuleEntity entity) {
    final MappingRuleRecordValue value = record.getValue();
    entity
        .setKey(value.getMappingRuleKey())
        .setMappingRuleId(value.getMappingRuleId())
        .setClaimName(value.getClaimName())
        .setClaimValue(value.getClaimValue());
  }

  @Override
  public void flush(final MappingRuleEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.delete(indexName, entity.getId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

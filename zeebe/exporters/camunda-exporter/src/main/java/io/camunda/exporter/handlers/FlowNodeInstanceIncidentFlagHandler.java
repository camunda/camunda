/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.List;
import java.util.Map;

/**
 * Sets the incident flag on the flow node instance documents named by the incident record. The
 * engine decides the keys, the flag is true for CREATED and false for RESOLVED.
 */
public class FlowNodeInstanceIncidentFlagHandler
    implements OrdinalIndexExportHandler<FlowNodeInstanceEntity, IncidentRecordValue> {

  private final String indexName;

  public FlowNodeInstanceIncidentFlagHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<FlowNodeInstanceEntity> getEntityType() {
    return FlowNodeInstanceEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<IncidentRecordValue> record) {
    return IncidentFlagKeysUtil.isFlagIntent(record)
        && !IncidentFlagKeysUtil.flowNodeFlagKeys(record.getValue()).isEmpty();
  }

  @Override
  public List<String> generateIds(final Record<IncidentRecordValue> record) {
    return IncidentFlagKeysUtil.flowNodeFlagKeys(record.getValue()).stream()
        .map(String::valueOf)
        .toList();
  }

  @Override
  public FlowNodeInstanceEntity createNewEntity(final String id) {
    return new FlowNodeInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<IncidentRecordValue> record, final FlowNodeInstanceEntity entity) {
    entity.setIncident(IncidentFlagKeysUtil.flagValue(record));
  }

  @Override
  public void flush(
      final TargetIndex index,
      final FlowNodeInstanceEntity entity,
      final BatchRequest batchRequest) {
    batchRequest.upsert(
        index,
        entity.getId(),
        entity,
        Map.of(FlowNodeInstanceTemplate.INCIDENT, entity.isIncident()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

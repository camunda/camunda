/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.List;
import java.util.Map;

/**
 * Sets the incident flag on the flow node list view documents named by the incident record. The
 * engine decides the keys, the flag is true for CREATED and false for RESOLVED.
 */
public class ListViewFlowNodeIncidentFlagHandler
    implements OrdinalIndexExportHandler<FlowNodeInstanceForListViewEntity, IncidentRecordValue> {

  private final String indexName;

  public ListViewFlowNodeIncidentFlagHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<FlowNodeInstanceForListViewEntity> getEntityType() {
    return FlowNodeInstanceForListViewEntity.class;
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
  public FlowNodeInstanceForListViewEntity createNewEntity(final String id) {
    return new FlowNodeInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<IncidentRecordValue> record, final FlowNodeInstanceForListViewEntity entity) {
    final IncidentRecordValue recordValue = record.getValue();
    final long key = Long.parseLong(entity.getId());
    final long processInstanceKey = IncidentFlagKeysUtil.processInstanceKeyOf(recordValue, key);
    entity
        .setId(entity.getId())
        .setKey(key)
        .setPartitionId(record.getPartitionId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setProcessInstanceKey(processInstanceKey)
        .setIncident(IncidentFlagKeysUtil.flagValue(record));
    entity.getJoinRelation().setParent(processInstanceKey);
  }

  @Override
  public void flush(
      final TargetIndex index,
      final FlowNodeInstanceForListViewEntity entity,
      final BatchRequest batchRequest) {
    batchRequest.upsertWithRouting(
        index,
        entity.getId(),
        entity,
        Map.of(ListViewTemplate.INCIDENT, entity.isIncident()),
        String.valueOf(entity.getProcessInstanceKey()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

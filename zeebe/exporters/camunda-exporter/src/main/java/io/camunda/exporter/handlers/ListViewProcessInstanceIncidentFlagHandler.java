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
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.List;
import java.util.Map;

/**
 * Sets the incident flag on the process instance list view documents named by the incident record.
 * The engine decides the keys, the flag is true for CREATED and false for RESOLVED.
 */
public class ListViewProcessInstanceIncidentFlagHandler
    implements OrdinalIndexExportHandler<ProcessInstanceForListViewEntity, IncidentRecordValue> {

  private final String indexName;

  public ListViewProcessInstanceIncidentFlagHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<ProcessInstanceForListViewEntity> getEntityType() {
    return ProcessInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<IncidentRecordValue> record) {
    return IncidentFlagKeysUtil.isFlagIntent(record)
        && !IncidentFlagKeysUtil.processInstanceFlagKeys(record.getValue()).isEmpty();
  }

  @Override
  public List<String> generateIds(final Record<IncidentRecordValue> record) {
    return IncidentFlagKeysUtil.processInstanceFlagKeys(record.getValue()).stream()
        .map(String::valueOf)
        .toList();
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(final String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<IncidentRecordValue> record, final ProcessInstanceForListViewEntity entity) {
    final long processInstanceKey = Long.parseLong(entity.getId());
    entity
        .setId(entity.getId())
        .setKey(processInstanceKey)
        .setProcessInstanceKey(processInstanceKey)
        .setPartitionId(record.getPartitionId())
        .setTenantId(tenantOrDefault(record.getValue().getTenantId()))
        .setIncident(IncidentFlagKeysUtil.flagValue(record));
  }

  @Override
  public void flush(
      final TargetIndex index,
      final ProcessInstanceForListViewEntity entity,
      final BatchRequest batchRequest) {
    batchRequest.upsert(
        index, entity.getId(), entity, Map.of(ListViewTemplate.INCIDENT, entity.isIncident()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

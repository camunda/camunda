/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.emptyToNull;
import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBusinessIdRecordValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflects a late Business ID assignment (ADR 0006) in the process-instance list-view document.
 * Only the {@code businessId} of the already-existing document is updated; no other field is
 * touched and no document is created, mirroring the forward-only, non-retroactive semantics of the
 * feature.
 */
public class ListViewProcessInstanceBusinessIdFromProcessInstanceBusinessIdHandler
    implements ExportHandler<
        ProcessInstanceForListViewEntity, ProcessInstanceBusinessIdRecordValue> {

  private final String indexName;

  public ListViewProcessInstanceBusinessIdFromProcessInstanceBusinessIdHandler(
      final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE_BUSINESS_ID;
  }

  @Override
  public Class<ProcessInstanceForListViewEntity> getEntityType() {
    return ProcessInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceBusinessIdRecordValue> record) {
    return record.getIntent() == ProcessInstanceBusinessIdIntent.ASSIGNED;
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceBusinessIdRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getProcessInstanceKey()));
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(final String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceBusinessIdRecordValue> record,
      final ProcessInstanceForListViewEntity entity) {
    final var recordValue = record.getValue();
    entity
        .setId(String.valueOf(recordValue.getProcessInstanceKey()))
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setKey(recordValue.getProcessInstanceKey())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setBusinessId(emptyToNull(recordValue.getBusinessId()));
  }

  @Override
  public void flush(
      final TargetIndex index,
      final ProcessInstanceForListViewEntity entity,
      final BatchRequest batchRequest) {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(ListViewTemplate.BUSINESS_ID, emptyToNull(entity.getBusinessId()));
    batchRequest.update(index, entity.getId(), updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.List;

public class SequenceFlowHandler
    implements ExportHandler<SequenceFlowEntity, ProcessInstanceRecordValue> {

  private static final String ID_PATTERN = "%s_%s";
  private final String indexName;

  public SequenceFlowHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<SequenceFlowEntity> getEntityType() {
    return SequenceFlowEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    final var recordValue = record.getValue();
    return List.of(formatId(recordValue.getProcessInstanceKey(), recordValue.getElementId()));
  }

  @Override
  public SequenceFlowEntity createNewEntity(final String id) {
    return new SequenceFlowEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record, final SequenceFlowEntity entity) {
    final var recordValue = record.getValue();
    entity
        .setId(formatId(recordValue.getProcessInstanceKey(), recordValue.getElementId()))
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setActivityId(recordValue.getElementId())
        .setTenantId(ExporterUtil.tenantOrDefault(recordValue.getTenantId()));
  }

  @Override
  public void flush(final SequenceFlowEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private static String formatId(final long processInstanceKey, final String elementId) {
    return String.format(ID_PATTERN, processInstanceKey, elementId);
  }
}

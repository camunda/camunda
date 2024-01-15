/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchBulkRequest;
import io.camunda.zeebe.exporter.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SequenceFlowHandler
    implements ExportHandler<SequenceFlowEntity, ProcessInstanceRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SequenceFlowHandler.class);

  private SequenceFlowTemplate sequenceFlowTemplate;

  public SequenceFlowHandler(SequenceFlowTemplate sequenceFlowTemplate) {
    this.sequenceFlowTemplate = sequenceFlowTemplate;
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
  public boolean handlesRecord(Record<ProcessInstanceRecordValue> record) {

    return ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN.equals(record.getIntent());
  }

  @Override
  public String generateId(Record<ProcessInstanceRecordValue> record) {
    final ProcessInstanceRecordValue recordValue = record.getValue();

    final StringBuilder sb = new StringBuilder();
    sb.append(recordValue.getProcessInstanceKey());
    sb.append("_");
    sb.append(recordValue.getElementId());

    return sb.toString();
  }

  @Override
  public SequenceFlowEntity createNewEntity(String id) {
    return new SequenceFlowEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<ProcessInstanceRecordValue> record, SequenceFlowEntity entity) {
    final ProcessInstanceRecordValue recordValue = record.getValue();

    entity
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setActivityId(recordValue.getElementId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));
  }

  @Override
  public void flush(SequenceFlowEntity entity, OperateElasticsearchBulkRequest batchRequest) {

    LOGGER.debug("Index sequence flow: id {}", entity.getId());
    batchRequest.index(sequenceFlowTemplate.getFullQualifiedName(), entity);
  }

  @Override
  public String getIndexName() {
    return sequenceFlowTemplate.getFullQualifiedName();
  }
}

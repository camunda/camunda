/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskProcessInstanceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.List;

public class UserTaskProcessInstanceHandler
    implements ExportHandler<TaskProcessInstanceEntity, ProcessInstanceRecordValue> {

  private final String indexName;

  public UserTaskProcessInstanceHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<TaskProcessInstanceEntity> getEntityType() {
    return TaskProcessInstanceEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent().equals(ELEMENT_ACTIVATING)
        && record.getValue().getBpmnElementType() != null
        && record.getValue().getBpmnElementType().equals(BpmnElementType.PROCESS);
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public TaskProcessInstanceEntity createNewEntity(final String id) {
    return new TaskProcessInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record, final TaskProcessInstanceEntity entity) {
    entity.setPartitionId(record.getPartitionId()).setTenantId(record.getValue().getTenantId());
    entity.setProcessInstanceId(record.getKey());
    final TaskJoinRelationship join = new TaskJoinRelationship();
    join.setName(TaskJoinRelationshipType.PROCESS.getType());

    entity.setJoin(join);
  }

  @Override
  public void flush(final TaskProcessInstanceEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

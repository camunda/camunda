/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.tasklist.TaskVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskVariableHandler
    implements ExportHandler<TaskVariableEntity, VariableRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskVariableHandler.class);

  private static final String ID_PATTERN = "%s-%s";
  protected final int variableSizeThreshold;
  private final String indexName;

  public UserTaskVariableHandler(final String indexName, final int variableSizeThreshold) {
    this.indexName = indexName;
    this.variableSizeThreshold = variableSizeThreshold;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.VARIABLE;
  }

  @Override
  public Class<TaskVariableEntity> getEntityType() {
    return TaskVariableEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<VariableRecordValue> record) {
    return !VariableIntent.MIGRATED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<VariableRecordValue> record) {
    final String id =
        ID_PATTERN.formatted(record.getValue().getScopeKey(), record.getValue().getName());

    /* Process Variable */
    if (record.getValue().getScopeKey() == record.getValue().getProcessInstanceKey()) {
      return List.of(id);
    }
    /*
     * Local Variable
     * Generate two IDs for process variable and local
     * */
    return List.of(id, id + TaskTemplate.LOCAL_VARIABLE_SUFFIX);
  }

  @Override
  public TaskVariableEntity createNewEntity(final String id) {
    return new TaskVariableEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<VariableRecordValue> record, final TaskVariableEntity entity) {
    entity
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setTenantId(record.getValue().getTenantId())
        .setKey(record.getKey())
        .setProcessInstanceId(record.getValue().getProcessInstanceKey())
        .setScopeKey(record.getValue().getScopeKey())
        .setName(record.getValue().getName());

    if (record.getValue().getValue().length() > variableSizeThreshold) {
      entity.setValue(record.getValue().getValue().substring(0, variableSizeThreshold));
      entity.setFullValue(record.getValue().getValue());
      entity.setIsTruncated(true);
    } else {
      entity.setValue(record.getValue().getValue());
      entity.setFullValue(null);
      entity.setIsTruncated(false);
    }

    final TaskJoinRelationship joinRelationship = new TaskJoinRelationship();
    joinRelationship.setParent(
        isLocalVariable(entity) ? entity.getScopeKey() : entity.getProcessInstanceId());
    joinRelationship.setName(
        isLocalVariable(entity)
            ? TaskJoinRelationshipType.LOCAL_VARIABLE.getType()
            : TaskJoinRelationshipType.PROCESS_VARIABLE.getType());
    entity.setJoin(joinRelationship);
  }

  @Override
  public void flush(final TaskVariableEntity entity, final BatchRequest batchRequest) {
    final Map<String, Object> updateFields = new HashMap<>();

    updateFields.put(TaskTemplate.VARIABLE_VALUE, entity.getValue());
    updateFields.put(TaskTemplate.VARIABLE_FULL_VALUE, entity.getFullValue());
    updateFields.put(TaskTemplate.IS_TRUNCATED, entity.getIsTruncated());

    batchRequest.upsertWithRouting(
        indexName, entity.getId(), entity, updateFields, String.valueOf(entity.getScopeKey()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private boolean isLocalVariable(final TaskVariableEntity entity) {
    return entity.getId().endsWith(TaskTemplate.LOCAL_VARIABLE_SUFFIX);
  }
}

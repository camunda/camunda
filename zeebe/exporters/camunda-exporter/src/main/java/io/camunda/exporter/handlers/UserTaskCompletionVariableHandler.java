/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.tasklist.TaskVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskCompletionVariableHandler
    implements ExportHandler<TaskVariableEntity, UserTaskRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UserTaskCompletionVariableHandler.class);

  private static final String ID_PATTERN = "%s-%s";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  protected final int variableSizeThreshold;
  private final String indexName;

  public UserTaskCompletionVariableHandler(
      final String indexName, final int variableSizeThreshold) {
    this.indexName = indexName;
    this.variableSizeThreshold = variableSizeThreshold;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.USER_TASK;
  }

  @Override
  public Class<TaskVariableEntity> getEntityType() {
    return TaskVariableEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<UserTaskRecordValue> record) {
    return UserTaskIntent.COMPLETED.equals(record.getIntent())
        && record.getValue().getVariables() != null
        && !record.getValue().getVariables().isEmpty();
  }

  @Override
  public List<String> generateIds(final Record<UserTaskRecordValue> record) {
    final List<String> variableIds = new ArrayList<>();
    record
        .getValue()
        .getVariables()
        .keySet()
        .forEach(
            variableName ->
                variableIds.add(
                    ID_PATTERN.formatted(record.getValue().getElementInstanceKey(), variableName)));
    return variableIds;
  }

  @Override
  public TaskVariableEntity createNewEntity(final String id) {
    return new TaskVariableEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<UserTaskRecordValue> record, final TaskVariableEntity entity) {
    final String variableName = entity.getId().split("-")[1];
    entity
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setName(variableName)
        .setTenantId(record.getValue().getTenantId())
        .setKey(record.getKey())
        .setProcessInstanceId(record.getValue().getProcessInstanceKey())
        .setScopeKey(record.getValue().getElementInstanceKey());

    String variableStringValue = "";
    final Object variableValue = record.getValue().getVariables().get(variableName);
    try {
      variableStringValue = MAPPER.writeValueAsString(variableValue);
    } catch (final JsonProcessingException e) {
      LOGGER.error(
          String.format(
              "Failed to parse variable '%s' value as string '%s'", variableName, variableValue),
          e);
    }

    if (variableStringValue.length() > variableSizeThreshold) {
      entity.setValue(variableStringValue.substring(0, variableSizeThreshold));
      entity.setFullValue(variableStringValue);
      entity.setIsTruncated(true);
    } else {
      entity.setValue(variableStringValue);
      entity.setFullValue(null);
      entity.setIsTruncated(false);
    }

    final TaskJoinRelationship joinRelationship = new TaskJoinRelationship();
    joinRelationship.setParent(entity.getScopeKey());
    joinRelationship.setName(TaskJoinRelationshipType.TASK_VARIABLE.getType());
    entity.setJoin(joinRelationship);
  }

  @Override
  public void flush(final TaskVariableEntity entity, final BatchRequest batchRequest) {
    final Map<String, Object> updateFields = new HashMap<>();

    updateFields.put(TaskTemplate.VARIABLE_VALUE, entity.getValue());
    updateFields.put(TaskTemplate.VARIABLE_FULL_VALUE, entity.getFullValue());
    updateFields.put(TaskTemplate.IS_TRUNCATED, entity.getIsTruncated());
    updateFields.put(TaskTemplate.JOIN_FIELD_NAME, entity.getJoin());

    batchRequest.upsertWithRouting(
        indexName, entity.getId(), entity, updateFields, String.valueOf(entity.getScopeKey()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

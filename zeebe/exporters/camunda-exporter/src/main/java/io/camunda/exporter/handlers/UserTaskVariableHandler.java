/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.handlers.UserTaskVariableHandler.UserTaskVariableBatch;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskVariableHandler
    implements ExportHandler<UserTaskVariableBatch, VariableRecordValue> {

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
  public Class<UserTaskVariableBatch> getEntityType() {
    return UserTaskVariableBatch.class;
  }

  @Override
  public boolean handlesRecord(final Record<VariableRecordValue> record) {
    return !VariableIntent.MIGRATED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<VariableRecordValue> record) {
    return List.of(
        ID_PATTERN.formatted(record.getValue().getScopeKey(), record.getValue().getName()));
  }

  @Override
  public UserTaskVariableBatch createNewEntity(final String id) {
    return new UserTaskVariableBatch().setId(id).setVariables(new ArrayList<>());
  }

  @Override
  public void updateEntity(
      final Record<VariableRecordValue> record, final UserTaskVariableBatch batchEntity) {

    final var processVariable = createVariableFromRecord(record);
    processVariable.setId(
        ID_PATTERN.formatted(record.getValue().getScopeKey(), record.getValue().getName()));

    final TaskJoinRelationship joinRelationship = new TaskJoinRelationship();
    joinRelationship.setParent(processVariable.getProcessInstanceId());
    joinRelationship.setName(TaskJoinRelationshipType.PROCESS_VARIABLE.getType());
    processVariable.setJoin(joinRelationship);
    batchEntity.getVariables().add(processVariable);

    if (record.getValue().getProcessInstanceKey() != record.getValue().getScopeKey()) {
      final TaskVariableEntity taskVariable = createVariableFromRecord(record);
      taskVariable.setId(
          ID_PATTERN.formatted(record.getValue().getScopeKey(), record.getValue().getName())
              + TaskTemplate.LOCAL_VARIABLE_SUFFIX);

      final TaskJoinRelationship localTaskJoinRelationship = new TaskJoinRelationship();
      localTaskJoinRelationship.setParent(taskVariable.getScopeKey());
      localTaskJoinRelationship.setName(TaskJoinRelationshipType.LOCAL_VARIABLE.getType());
      taskVariable.setJoin(localTaskJoinRelationship);
      batchEntity.getVariables().add(taskVariable);
    }
  }

  @Override
  public void flush(final UserTaskVariableBatch entity, final BatchRequest batchRequest) {
    entity
        .getVariables()
        .forEach(
            v -> {
              final Map<String, Object> updateFields = new HashMap<>();
              updateFields.put(TaskTemplate.VARIABLE_VALUE, v.getValue());
              updateFields.put(TaskTemplate.VARIABLE_FULL_VALUE, v.getFullValue());
              updateFields.put(TaskTemplate.IS_TRUNCATED, v.getIsTruncated());
              batchRequest.upsertWithRouting(
                  indexName, v.getId(), v, updateFields, String.valueOf(v.getProcessInstanceId()));
            });
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private TaskVariableEntity createVariableFromRecord(final Record<VariableRecordValue> record) {
    final var variable =
        new TaskVariableEntity()
            .setPartitionId(record.getPartitionId())
            .setPosition(record.getPosition())
            .setTenantId(record.getValue().getTenantId())
            .setKey(record.getKey())
            .setProcessInstanceId(record.getValue().getProcessInstanceKey())
            .setScopeKey(record.getValue().getScopeKey())
            .setName(record.getValue().getName());
    setVariableValues(record, variable);

    return variable;
  }

  private void setVariableValues(
      final Record<VariableRecordValue> record, final TaskVariableEntity taskVariable) {
    if (record.getValue().getValue().length() > variableSizeThreshold) {
      taskVariable.setValue(record.getValue().getValue().substring(0, variableSizeThreshold));
      taskVariable.setFullValue(record.getValue().getValue());
      taskVariable.setIsTruncated(true);
    } else {
      taskVariable.setValue(record.getValue().getValue());
      taskVariable.setFullValue(null);
      taskVariable.setIsTruncated(false);
    }
  }

  public static final class UserTaskVariableBatch
      extends AbstractExporterEntity<UserTaskVariableBatch> {
    private List<TaskVariableEntity> variables;

    public List<TaskVariableEntity> getVariables() {
      return variables;
    }

    public UserTaskVariableBatch setVariables(final List<TaskVariableEntity> variables) {
      this.variables = variables;
      return this;
    }
  }
}

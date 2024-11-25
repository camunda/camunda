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
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.tasklist.TaskVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public UserTaskVariableBatch createNewEntity(final String id) {
    return new UserTaskVariableBatch().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<VariableRecordValue> record, final UserTaskVariableBatch entity) {

    final var processVariable =
        new TaskVariableEntity()
            .setId(
                ID_PATTERN.formatted(record.getValue().getScopeKey(), record.getValue().getName()))
            .setPartitionId(record.getPartitionId())
            .setPosition(record.getPosition())
            .setTenantId(record.getValue().getTenantId())
            .setKey(record.getKey())
            .setProcessInstanceId(record.getValue().getProcessInstanceKey())
            .setScopeKey(record.getValue().getScopeKey())
            .setName(record.getValue().getName());

    if (record.getValue().getValue().length() > variableSizeThreshold) {
      processVariable.setValue(record.getValue().getValue().substring(0, variableSizeThreshold));
      processVariable.setFullValue(record.getValue().getValue());
      processVariable.setIsTruncated(true);
    } else {
      processVariable.setValue(record.getValue().getValue());
      processVariable.setFullValue(null);
      processVariable.setIsTruncated(false);
    }

    final TaskJoinRelationship joinRelationship = new TaskJoinRelationship();
    joinRelationship.setParent(processVariable.getProcessInstanceId());
    joinRelationship.setName(TaskJoinRelationshipType.PROCESS_VARIABLE.getType());
    processVariable.setJoin(joinRelationship);

    TaskVariableEntity taskVariable = null;
    if (!Objects.equals(
        record.getValue().getProcessInstanceKey(), record.getValue().getScopeKey())) {
      taskVariable =
          new TaskVariableEntity()
              .setId(
                  ID_PATTERN.formatted(record.getValue().getScopeKey(), record.getValue().getName())
                      + "-local")
              .setPartitionId(record.getPartitionId())
              .setPosition(record.getPosition())
              .setTenantId(record.getValue().getTenantId())
              .setKey(record.getKey())
              .setProcessInstanceId(record.getValue().getProcessInstanceKey())
              .setScopeKey(record.getValue().getScopeKey())
              .setName(record.getValue().getName());

      if (record.getValue().getValue().length() > variableSizeThreshold) {
        taskVariable.setValue(record.getValue().getValue().substring(0, variableSizeThreshold));
        taskVariable.setFullValue(record.getValue().getValue());
        taskVariable.setIsTruncated(true);
      } else {
        taskVariable.setValue(record.getValue().getValue());
        taskVariable.setFullValue(null);
        taskVariable.setIsTruncated(false);
      }

      final TaskJoinRelationship localTaskJoinRelationship = new TaskJoinRelationship();
      localTaskJoinRelationship.setParent(taskVariable.getScopeKey());
      localTaskJoinRelationship.setName(TaskJoinRelationshipType.LOCAL_VARIABLE.getType());
      taskVariable.setJoin(localTaskJoinRelationship);
    }

    final var myList = new ArrayList<TaskVariableEntity>();
    myList.add(processVariable);
    myList.add(taskVariable);
    entity.setVariables(myList);
  }

  @Override
  public void flush(final UserTaskVariableBatch batch, final BatchRequest batchRequest) {

    batch
        .getVariables()
        .forEach(
            entity -> {
              if (entity != null) {
                final Map<String, Object> updateFields = new HashMap<>();

                updateFields.put(TaskTemplate.VARIABLE_VALUE, entity.getValue());
                updateFields.put(TaskTemplate.VARIABLE_FULL_VALUE, entity.getFullValue());
                updateFields.put(TaskTemplate.IS_TRUNCATED, entity.getIsTruncated());

                batchRequest.upsertWithRouting(
                    indexName,
                    entity.getId(),
                    entity,
                    updateFields,
                    String.valueOf(entity.getScopeKey()));
              }
            });
  }

  @Override
  public String getIndexName() {
    return indexName;
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

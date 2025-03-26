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
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.exporter.handlers.UserTaskCompletionVariableHandler.SnapshotTaskVariableBatch;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.tasklist.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskCompletionVariableHandler
    implements ExportHandler<SnapshotTaskVariableBatch, UserTaskRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UserTaskCompletionVariableHandler.class);

  private static final String ID_PATTERN = "%s-%s";
  protected final int variableSizeThreshold;
  private final String indexName;
  private final ObjectWriter objectWriter;

  public UserTaskCompletionVariableHandler(
      final String indexName, final int variableSizeThreshold, final ObjectMapper objectMapper) {
    this.indexName = indexName;
    this.variableSizeThreshold = variableSizeThreshold;
    objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.USER_TASK;
  }

  @Override
  public Class<SnapshotTaskVariableBatch> getEntityType() {
    return SnapshotTaskVariableBatch.class;
  }

  @Override
  public boolean handlesRecord(final Record<UserTaskRecordValue> record) {
    return UserTaskIntent.COMPLETED.equals(record.getIntent())
        && record.getValue().getVariables() != null
        && !record.getValue().getVariables().isEmpty();
  }

  @Override
  public List<String> generateIds(final Record<UserTaskRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getUserTaskKey()));
  }

  @Override
  public SnapshotTaskVariableBatch createNewEntity(final String id) {
    return new SnapshotTaskVariableBatch(id, new ArrayList<>());
  }

  @Override
  public void updateEntity(
      final Record<UserTaskRecordValue> record, final SnapshotTaskVariableBatch entity) {
    final var snapshotVariableCollection = entity.variables();
    final var recordValue = record.getValue();
    final var recordVariables = recordValue.getVariables();
    recordVariables.entrySet().stream()
        .map(e -> createSnapshotVariableEntity(e, record))
        .forEach(snapshotVariableCollection::add);
  }

  @Override
  public void flush(final SnapshotTaskVariableBatch entity, final BatchRequest batchRequest) {
    entity.variables().forEach(v -> flushSnapshotTaskVariableEntity(v, batchRequest));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private void flushSnapshotTaskVariableEntity(
      final SnapshotTaskVariableEntity entity, final BatchRequest batchRequest) {
    final var updateFields = new HashMap<String, Object>();
    updateFields.put(SnapshotTaskVariableTemplate.VALUE, entity.getValue());
    updateFields.put(SnapshotTaskVariableTemplate.FULL_VALUE, entity.getFullValue());
    updateFields.put(SnapshotTaskVariableTemplate.IS_PREVIEW, entity.getIsPreview());
    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }

  private SnapshotTaskVariableEntity createSnapshotVariableEntity(
      final Entry<String, Object> e, final Record<UserTaskRecordValue> record) {
    final var recordValue = record.getValue();
    final var userTaskKey = recordValue.getUserTaskKey();

    final var variableName = e.getKey();
    final var variableValue = e.getValue();

    final var variableValueAsString = toJsonString(variableValue);
    final var snapshotVariableEntity =
        new SnapshotTaskVariableEntity()
            .setId(String.format(ID_PATTERN, userTaskKey, variableName))
            .setKey(record.getKey())
            .setPartitionId(record.getPartitionId())
            .setName(e.getKey())
            .setTenantId(recordValue.getTenantId())
            .setProcessInstanceKey(recordValue.getProcessInstanceKey())
            .setTaskId(String.valueOf(record.getValue().getUserTaskKey()))
            .setFullValue(variableValueAsString);

    if (variableValueAsString.length() > variableSizeThreshold) {
      // store preview
      snapshotVariableEntity
          .setValue(variableValueAsString.substring(0, variableSizeThreshold))
          .setIsPreview(true);
    } else {
      snapshotVariableEntity.setValue(variableValueAsString).setIsPreview(false);
    }
    return snapshotVariableEntity;
  }

  private String toJsonString(final Object value) {
    try {
      return objectWriter.writeValueAsString(value);
    } catch (final JsonProcessingException e) {
      LOGGER.error("Failed to parse variable value '{}'", value, e);
      return "";
    }
  }

  public record SnapshotTaskVariableBatch(String id, List<SnapshotTaskVariableEntity> variables)
      implements ExporterEntity<SnapshotTaskVariableBatch> {

    @Override
    public String getId() {
      return id;
    }

    @Override
    public SnapshotTaskVariableBatch setId(final String id) {
      throw new UnsupportedOperationException("Not allowed to set an id");
    }
  }
}

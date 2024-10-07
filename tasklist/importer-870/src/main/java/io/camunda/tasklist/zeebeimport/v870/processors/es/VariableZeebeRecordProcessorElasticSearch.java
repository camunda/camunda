/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.entities.listview.ListViewJoinRelation;
import io.camunda.tasklist.entities.listview.VariableListViewEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TasklistListViewTemplate;
import io.camunda.tasklist.zeebeimport.v870.record.Intent;
import io.camunda.tasklist.zeebeimport.v870.record.value.VariableRecordValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class VariableZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(VariableZeebeRecordProcessorElasticSearch.class);

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private VariableIndex variableIndex;

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private TasklistListViewTemplate tasklistListViewTemplate;

  public void processVariableRecord(final Record record, final BulkRequest bulkRequest)
      throws PersistenceException {
    final VariableRecordValueImpl recordValue = (VariableRecordValueImpl) record.getValue();

    // update variable
    if (record.getIntent().name() != Intent.MIGRATED.name()) {
      bulkRequest.add(persistVariable(record, recordValue));
      bulkRequest.add(persistVariableToListView(record, recordValue)); // tasklist-list-view
    }
  }

  private UpdateRequest persistVariable(
      final Record record, final VariableRecordValueImpl recordValue) throws PersistenceException {
    final VariableEntity variableEntity = getVariableEntity(record, recordValue);
    return getVariableQuery(variableEntity);
  }

  private UpdateRequest getVariableQuery(final VariableEntity entity) throws PersistenceException {
    try {
      LOGGER.debug("Variable instance for list view: id {}", entity.getId());
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(VariableIndex.VALUE, entity.getValue());
      updateFields.put(VariableIndex.FULL_VALUE, entity.getFullValue());
      updateFields.put(VariableIndex.IS_PREVIEW, entity.getIsPreview());

      return new UpdateRequest()
          .index(variableIndex.getFullQualifiedName())
          .id(entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert variable instance [%s]  for list view",
              entity.getId()),
          e);
    }
  }

  private UpdateRequest persistVariableToListView(
      final Record record, final VariableRecordValueImpl recordValue) throws PersistenceException {
    final VariableEntity variableEntity = getVariableEntity(record, recordValue);
    final VariableListViewEntity variableListViewEntity =
        createVariableInputToListView(variableEntity);

    if (isTaskOrSubProcessVariable(variableEntity)) {
      variableListViewEntity.setJoin(
          createListViewJoinRelation("taskVariable", variableListViewEntity.getScopeKey()));
    } else if (isProcessScope(variableEntity)) {
      variableListViewEntity.setJoin(
          createListViewJoinRelation("processVariable", variableListViewEntity.getScopeKey()));
    } else {
      throw new PersistenceException(
          String.format(
              "Error to associate Variable with parent. Variable id: [%s]",
              variableEntity.getId()));
    }
    return prepareUpdateRequest(variableListViewEntity);
  }

  private VariableListViewEntity createVariableInputToListView(final VariableEntity entity) {
    return new VariableListViewEntity(entity);
  }

  private ListViewJoinRelation createListViewJoinRelation(
      final String name, final String parentId) {
    final var result = new ListViewJoinRelation();
    result.setName(name);
    result.setParent(Long.valueOf(parentId));
    return result;
  }

  private boolean isProcessScope(final VariableEntity entity) {
    return Objects.equals(entity.getProcessInstanceId(), entity.getScopeFlowNodeId());
  }

  private boolean isTaskOrSubProcessVariable(final VariableEntity entity) {
    return !Objects.equals(entity.getProcessInstanceId(), entity.getScopeFlowNodeId());
  }

  private VariableEntity getVariableEntity(
      final Record record, final VariableRecordValueImpl recordValue) {
    final VariableEntity entity = new VariableEntity();
    entity.setId(
        VariableEntity.getIdBy(String.valueOf(recordValue.getScopeKey()), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setScopeFlowNodeId(String.valueOf(recordValue.getScopeKey()));
    entity.setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()));
    entity.setName(recordValue.getName());
    entity.setTenantId(recordValue.getTenantId());
    if (recordValue.getValue().length()
        > tasklistProperties.getImporter().getVariableSizeThreshold()) {
      // store preview
      entity.setValue(
          recordValue
              .getValue()
              .substring(0, tasklistProperties.getImporter().getVariableSizeThreshold()));
      entity.setIsPreview(true);
    } else {
      entity.setValue(recordValue.getValue());
    }
    entity.setFullValue(recordValue.getValue());
    return entity;
  }

  private UpdateRequest prepareUpdateRequest(final VariableListViewEntity variableListViewEntity)
      throws PersistenceException {
    try {
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TasklistListViewTemplate.VARIABLE_VALUE, variableListViewEntity.getValue());
      updateFields.put(
          TasklistListViewTemplate.VARIABLE_FULL_VALUE, variableListViewEntity.getValue());
      updateFields.put(TasklistListViewTemplate.IS_PREVIEW, variableListViewEntity.getIsPreview());

      final UpdateRequest request =
          new UpdateRequest()
              .index(tasklistListViewTemplate.getFullQualifiedName())
              .id(variableListViewEntity.getId())
              .upsert(objectMapper.writeValueAsString(variableListViewEntity), XContentType.JSON)
              .routing(variableListViewEntity.getScopeKey())
              .doc(updateFields)
              .retryOnConflict(UPDATE_RETRY_COUNT);

      return request;
    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert task instance [%s]",
              variableListViewEntity.getId()),
          e);
    }
  }
}

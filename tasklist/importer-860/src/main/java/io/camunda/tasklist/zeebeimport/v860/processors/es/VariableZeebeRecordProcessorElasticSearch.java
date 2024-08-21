/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v860.processors.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskVariableSnapshotEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TasklistTaskVariableSnapshotTemplate;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.tasklist.zeebeimport.v860.record.Intent;
import io.camunda.tasklist.zeebeimport.v860.record.value.VariableRecordValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  @Autowired private TaskStore taskStore;

  @Autowired private TasklistTaskVariableSnapshotTemplate taskVariableSnapshotTemplate;

  public void processVariableRecord(final Record record, final BulkRequest bulkRequest)
      throws PersistenceException {
    final VariableRecordValueImpl recordValue = (VariableRecordValueImpl) record.getValue();

    // update variable
    if (record.getIntent().name() != Intent.MIGRATED.name()) {
      bulkRequest.add(persistVariable(record, recordValue));
      bulkRequest.add(persistTaskVariableSnapshot(record, recordValue));
    }
  }

  private UpdateRequest persistVariable(
      final Record record, final VariableRecordValueImpl recordValue) throws PersistenceException {
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
    return getVariableQuery(entity);
  }

  private UpdateRequest persistTaskVariableSnapshot(final Record record, final VariableRecordValueImpl recordValue)
      throws PersistenceException {
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

    final TaskVariableSnapshotEntity snapshot = new TaskVariableSnapshotEntity();

    Optional.ofNullable(entity.getValue()).ifPresent(snapshot::setVariableValue);
    Optional.ofNullable(entity.getFullValue()).ifPresent(snapshot::setVariableFullValue);
    Optional.ofNullable(entity.getValue()).ifPresent(snapshot::setVariableValue);
    Optional.of(entity.getIsPreview()).ifPresent(snapshot::setPreview);

    final TaskQuery taskQuery = new TaskQuery().setProcessInstanceId(entity.getProcessInstanceId());
    final List<TaskSearchView> taskEntityList = taskStore.getTasks(taskQuery);

    /*taskEntityList.stream().collect(),

    Optional.ofNullable(entity.getId()).ifPresent(snapshot::setId);
    Optional.of(entity.getKey()).ifPresent(snapshot::setKey);
    Optional.ofNullable(entity.getFlowNodeBpmnId()).ifPresent(snapshot::setFlowNodeBpmnId);
    Optional.ofNullable(entity.getFlowNodeInstanceId()).ifPresent(snapshot::setFlowNodeInstanceId);
    Optional.of(entity.getPartitionId()).ifPresent(snapshot::setPartitionId);
    Optional.ofNullable(entity.getCompletionTime()).map(Object::toString).ifPresent(snapshot::setCompletionTime);
    Optional.ofNullable(entity.getProcessInstanceId()).ifPresent(snapshot::setProcessInstanceId);
    Optional.ofNullable(entity.getAssignee()).ifPresent(snapshot::setAssignee);
    Optional.ofNullable(entity.getCreationTime()).map(Object::toString).ifPresent(snapshot::setCreationTime);
    Optional.ofNullable(entity.getProcessDefinitionVersion()).ifPresent(snapshot::setProcessDefinitionVersion);
    Optional.ofNullable(entity.getPriority()).ifPresent(snapshot::setPriority);
    Optional.ofNullable(entity.getCandidateGroups()).ifPresent(snapshot::setCandidateGroups);
    Optional.ofNullable(entity.getCandidateUsers()).ifPresent(snapshot::setCandidateUsers);
    Optional.ofNullable(entity.getBpmnProcessId()).ifPresent(snapshot::setBpmnProcessId);
    Optional.ofNullable(entity.getProcessDefinitionId()).ifPresent(snapshot::setProcessDefinitionId);
    Optional.ofNullable(entity.getTenantId()).ifPresent(snapshot::setTenantId);
    Optional.ofNullable(entity.getExternalFormReference()).ifPresent(snapshot::setExternalFormReference);
    Optional.ofNullable(entity.getCustomHeaders()).ifPresent(snapshot::setCustomHeaders);
    Optional.ofNullable(entity.getFormKey()).ifPresent(snapshot::setFormKey);*/


    try {
      return new UpdateRequest()
          .index(taskVariableSnapshotTemplate.getFullQualifiedName())
          .id(snapshot.getId())
          .upsert(objectMapper.writeValueAsString(snapshot), XContentType.JSON)
          .doc(objectMapper.writeValueAsString(snapshot), XContentType.JSON)
          .retryOnConflict(UPDATE_RETRY_COUNT);
    } catch (final IOException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to upsert task instance [%s]", entity.getId()),
          e);
    }
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
}

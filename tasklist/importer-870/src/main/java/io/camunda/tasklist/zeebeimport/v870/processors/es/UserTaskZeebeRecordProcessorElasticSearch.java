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
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.zeebeimport.v870.processors.common.UserTaskRecordToTaskEntityMapper;
import io.camunda.tasklist.zeebeimport.v870.processors.common.UserTaskRecordToVariableEntityMapper;
import io.camunda.webapps.schema.descriptors.tasklist.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class UserTaskZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UserTaskZeebeRecordProcessorElasticSearch.class);

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private UserTaskRecordToVariableEntityMapper userTaskRecordToVariableEntityMapper;

  @Autowired
  @Qualifier("tasklistSnapshotTaskVariableTemplate")
  private SnapshotTaskVariableTemplate variableIndex;

  @Autowired private UserTaskRecordToTaskEntityMapper userTaskRecordToTaskEntityMapper;

  public void processUserTaskRecord(
      final Record<UserTaskRecordValue> record, final BulkRequest bulkRequest)
      throws PersistenceException {

    final Optional<TaskEntity> taskEntity = userTaskRecordToTaskEntityMapper.map(record);
    if (taskEntity.isPresent()) {
      bulkRequest.add(getTaskQuery(taskEntity.get(), record));
      // Variables
      if (!record.getValue().getVariables().isEmpty()) {
        final List<SnapshotTaskVariableEntity> variables =
            userTaskRecordToVariableEntityMapper.mapVariables(record);
        for (final SnapshotTaskVariableEntity variable : variables) {
          bulkRequest.add(getVariableQuery(variable));
        }
      }
    }
    // else skip task
  }

  private UpdateRequest getTaskQuery(final TaskEntity entity, final Record record)
      throws PersistenceException {
    try {
      final Map<String, Object> updateFields =
          userTaskRecordToTaskEntityMapper.getUpdateFieldsMap(entity, record);

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest()
          .index(taskTemplate.getFullQualifiedName())
          .id(entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(jsonMap)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (final IOException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to upsert task instance [%s]", entity.getId()),
          e);
    }
  }

  private UpdateRequest getVariableQuery(final SnapshotTaskVariableEntity variable)
      throws PersistenceException {
    try {
      LOGGER.debug("Variable instance for list view: id {}", variable.getId());
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(SnapshotTaskVariableTemplate.VALUE, variable.getValue());
      updateFields.put(SnapshotTaskVariableTemplate.FULL_VALUE, variable.getFullValue());
      updateFields.put(SnapshotTaskVariableTemplate.IS_PREVIEW, variable.getIsPreview());

      return new UpdateRequest()
          .index(variableIndex.getFullQualifiedName())
          .id(variable.getId())
          .upsert(objectMapper.writeValueAsString(variable), XContentType.JSON)
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT);
    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert variable instance [%s]  for list view",
              variable.getId()),
          e);
    }
  }
}

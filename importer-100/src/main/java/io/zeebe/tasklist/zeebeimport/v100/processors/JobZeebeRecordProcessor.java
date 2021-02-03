/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v100.processors;

import static io.zeebe.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.zeebe.tasklist.zeebeimport.v100.record.Intent.CANCELED;
import static io.zeebe.tasklist.zeebeimport.v100.record.Intent.COMPLETED;
import static io.zeebe.tasklist.zeebeimport.v100.record.Intent.CREATED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.es.schema.templates.TaskTemplate;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.DateUtil;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.zeebeimport.v100.record.value.JobRecordValueImpl;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JobZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobZeebeRecordProcessor.class);

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TasklistProperties tasklistProperties;

  public void processJobRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final JobRecordValueImpl recordValue = (JobRecordValueImpl) record.getValue();
    if (recordValue.getType().equals(tasklistProperties.getImporter().getJobType())) {
      bulkRequest.add(persistTask(record, recordValue));
    }
    // else skip task
  }

  private UpdateRequest persistTask(Record record, JobRecordValueImpl recordValue)
      throws PersistenceException {
    final TaskEntity entity =
        new TaskEntity()
            .setId(String.valueOf(record.getKey()))
            .setKey(record.getKey())
            .setPartitionId(record.getPartitionId())
            .setFlowNodeBpmnId(recordValue.getElementId())
            .setFlowNodeInstanceId(String.valueOf(recordValue.getElementInstanceKey()))
            .setWorkflowInstanceId(String.valueOf(recordValue.getWorkflowInstanceKey()))
            .setBpmnProcessId(recordValue.getBpmnProcessId())
            .setWorkflowId(String.valueOf(recordValue.getWorkflowKey()));
    final String taskState = record.getIntent().name();
    LOGGER.debug("JobState {}", taskState);
    if (taskState.equals(CANCELED.name())) {
      entity
          .setState(TaskState.CANCELED)
          .setCompletionTime(
              DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else if (taskState.equals(COMPLETED.name())) {
      entity
          .setState(TaskState.COMPLETED)
          .setCompletionTime(
              DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else if (taskState.equals(CREATED.name())) {
      entity
          .setState(TaskState.CREATED)
          .setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else {
      LOGGER.warn(String.format("TaskState %s not supported", taskState));
    }
    return getTaskQuery(entity);
  }

  private UpdateRequest getTaskQuery(TaskEntity entity) throws PersistenceException {
    try {
      LOGGER.debug("Task instance: id {}", entity.getId());
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskTemplate.STATE, entity.getState());
      updateFields.put(TaskTemplate.COMPLETION_TIME, entity.getCompletionTime());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest(
              taskTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(jsonMap)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to upsert task instance [%s]", entity.getId()),
          e);
    }
  }
}

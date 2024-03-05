/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v850.processors.os;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.v850.processors.common.UserTaskRecordToTaskEntityMapper;
import io.camunda.tasklist.zeebeimport.v850.record.Intent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class UserTaskZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UserTaskZeebeRecordProcessorOpenSearch.class);

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private UserTaskRecordToTaskEntityMapper userTaskRecordToTaskEntityMapper;

  public void processUserTaskRecord(
      Record<UserTaskRecordValue> record, List<BulkOperation> operations) {
    final Optional<TaskEntity> taskEntity = userTaskRecordToTaskEntityMapper.map(record);
    if (taskEntity.isPresent()) {
      operations.add(getTaskQuery(taskEntity.get(), (Intent) record.getIntent()));
    }
    // else skip task
  }

  private BulkOperation getTaskQuery(TaskEntity entity, Intent intent) {
    final Map<String, Object> updateFields =
        userTaskRecordToTaskEntityMapper.getUpdateFieldsMap(entity, intent);
    return new BulkOperation.Builder()
        .update(
            UpdateOperation.of(
                u ->
                    u.index(taskTemplate.getFullQualifiedName())
                        .id(entity.getId())
                        .document(CommonUtils.getJsonObjectFromEntity(updateFields))
                        .upsert(CommonUtils.getJsonObjectFromEntity(entity))
                        .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT)))
        .build();
  }
}

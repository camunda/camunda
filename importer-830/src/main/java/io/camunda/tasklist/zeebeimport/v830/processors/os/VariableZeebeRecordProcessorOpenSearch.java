/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v830.processors.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.v830.record.Intent;
import io.camunda.tasklist.zeebeimport.v830.record.value.VariableRecordValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class VariableZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(VariableZeebeRecordProcessorOpenSearch.class);

  private static final Set<String> VARIABLE_STATES = new HashSet<>();

  static {
    VARIABLE_STATES.add(Intent.CREATED.name());
    VARIABLE_STATES.add(Intent.UPDATED.name());
  }

  @Autowired private ObjectMapper objectMapper;

  @Autowired private VariableIndex variableIndex;

  @Autowired private TasklistProperties tasklistProperties;

  public void processVariableRecord(
      Record<VariableRecordValueImpl> record, List<BulkOperation> operations)
      throws PersistenceException {
    final VariableRecordValueImpl recordValue = record.getValue();

    operations.add(persistVariable(record, recordValue));
  }

  private BulkOperation persistVariable(
      Record<VariableRecordValueImpl> record, VariableRecordValueImpl recordValue) {

    final VariableEntity entity =
        new VariableEntity()
            .setId(
                VariableEntity.getIdBy(
                    String.valueOf(recordValue.getScopeKey()), recordValue.getName()))
            .setKey(record.getKey())
            .setPartitionId(record.getPartitionId())
            .setScopeFlowNodeId(String.valueOf(recordValue.getScopeKey()))
            .setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()))
            .setName(recordValue.getName());
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
    entity.setTenantId(recordValue.getTenantId());
    return getVariableQuery(entity);
  }

  private BulkOperation getVariableQuery(VariableEntity entity) {
    LOGGER.debug("Variable instance for list view: id {}", entity.getId());
    return new BulkOperation.Builder()
        .update(
            UpdateOperation.of(
                up ->
                    up.index(variableIndex.getFullQualifiedName())
                        .id(entity.getId())
                        .document(CommonUtils.getJsonObjectFromEntity(entity))
                        .docAsUpsert(true)
                        .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT)))
        .build();
  }
}

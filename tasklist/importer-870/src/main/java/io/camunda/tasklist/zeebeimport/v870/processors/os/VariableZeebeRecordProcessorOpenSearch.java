/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.os;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.v870.record.Intent;
import io.camunda.tasklist.zeebeimport.v870.record.value.VariableRecordValueImpl;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.listview.VariableForListViewEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import java.util.*;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class VariableZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(VariableZeebeRecordProcessorOpenSearch.class);

  @Autowired
  @Qualifier("tasklistVariableTemplate")
  private VariableTemplate variableIndex;

  @Autowired private TasklistProperties tasklistProperties;

  public void processVariableRecord(final Record record, final List<BulkOperation> operations)
      throws PersistenceException {
    final VariableRecordValueImpl recordValue = (VariableRecordValueImpl) record.getValue();

    // update variable
    if (record.getIntent().name() != Intent.MIGRATED.name()) {
      operations.add(persistVariable(record, recordValue));
    }
  }

  private BulkOperation persistVariable(
      final Record record, final VariableRecordValueImpl recordValue) throws PersistenceException {
    final VariableEntity entity = getVariableEntity(record, recordValue);
    return getVariableQuery(entity);
  }

  private BulkOperation getVariableQuery(final VariableEntity entity) throws PersistenceException {
    LOGGER.debug("Variable instance for list view: id {}", entity.getId());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(VariableTemplate.VALUE, entity.getValue());
    updateFields.put(VariableTemplate.FULL_VALUE, entity.getFullValue());
    updateFields.put(VariableTemplate.IS_PREVIEW, entity.getIsPreview());

    return new BulkOperation.Builder()
        .update(
            up ->
                up.index(variableIndex.getFullQualifiedName())
                    .id(entity.getId())
                    .document(CommonUtils.getJsonObjectFromEntity(updateFields))
                    .upsert(CommonUtils.getJsonObjectFromEntity(entity))
                    .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT))
        .build();
  }

  private VariableEntity getVariableEntity(
      final Record record, final VariableRecordValueImpl recordValue) {
    final VariableEntity entity =
        new VariableEntity()
            .setId(String.format("%d-%s", recordValue.getScopeKey(), recordValue.getName()))
            .setId(
                VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()))
            .setKey(record.getKey())
            .setPartitionId(record.getPartitionId())
            .setScopeKey(recordValue.getScopeKey())
            .setProcessInstanceKey(recordValue.getProcessInstanceKey())
            .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
            .setBpmnProcessId(recordValue.getBpmnProcessId())
            .setName(recordValue.getName())
            .setTenantId(recordValue.getTenantId())
            .setPosition(record.getPosition());

    final var variableSizeThreshold = tasklistProperties.getImporter().getVariableSizeThreshold();
    if (recordValue.getValue().length() > variableSizeThreshold) {
      entity.setValue(recordValue.getValue().substring(0, variableSizeThreshold));
      entity.setFullValue(recordValue.getValue());
      entity.setIsPreview(true);
    } else {
      entity.setValue(recordValue.getValue());
      entity.setFullValue(null);
      entity.setIsPreview(false);
    }
    return entity;
  }
}

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
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.zeebeimport.v870.record.Intent;
import io.camunda.tasklist.zeebeimport.v870.record.value.VariableRecordValueImpl;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.listview.VariableForListViewEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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

  @Autowired
  @Qualifier("tasklistVariableTemplate")
  private VariableTemplate variableIndex;

  @Autowired private TasklistProperties tasklistProperties;

  public void processVariableRecord(final Record record, final BulkRequest bulkRequest)
      throws PersistenceException {
    final VariableRecordValueImpl recordValue = (VariableRecordValueImpl) record.getValue();

    // update variable
    if (record.getIntent().name() != Intent.MIGRATED.name()) {
      bulkRequest.add(persistVariable(record, recordValue));
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
      updateFields.put(VariableTemplate.VALUE, entity.getValue());
      updateFields.put(VariableTemplate.FULL_VALUE, entity.getFullValue());
      updateFields.put(VariableTemplate.IS_PREVIEW, entity.getIsPreview());

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

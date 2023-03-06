/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_1.processors;

import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(VariableZeebeRecordProcessor.class);

  private static final Set<String> VARIABLE_STATES = new HashSet<>();

  static {
    VARIABLE_STATES.add(VariableIntent.CREATED.name());
    VARIABLE_STATES.add(VariableIntent.UPDATED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private VariableTemplate variableTemplate;

  @Autowired
  private OperateProperties operateProperties;

  public void processVariableRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    VariableRecordValue recordValue = (VariableRecordValue)record.getValue();

    //update variable
    bulkRequest.add(persistVariable(record, recordValue));

  }

  private UpdateRequest persistVariable(Record record, VariableRecordValue recordValue) throws PersistenceException {
    VariableEntity entity = new VariableEntity();
    entity.setId(VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setScopeKey(recordValue.getScopeKey());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());
    entity.setName(recordValue.getName());
    if (recordValue.getValue().length() > operateProperties.getImporter().getVariableSizeThreshold()) {
      // store preview
      entity.setValue(
          recordValue
              .getValue()
              .substring(0, operateProperties.getImporter().getVariableSizeThreshold()));
      entity.setFullValue(recordValue.getValue());
      entity.setIsPreview(true);
    } else {
      entity.setValue(recordValue.getValue());
      entity.setFullValue(null);
      entity.setIsPreview(false);
    }
    return getVariableQuery(entity);
  }

  private UpdateRequest getVariableQuery(VariableEntity entity) throws PersistenceException {
    try {
      logger.debug("Variable instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(VariableTemplate.VALUE, entity.getValue());
      updateFields.put(VariableTemplate.FULL_VALUE, entity.getFullValue());
      updateFields.put(VariableTemplate.IS_PREVIEW, entity.getIsPreview());

      return new UpdateRequest().index(variableTemplate.getFullQualifiedName()).id(entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert variable instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert variable instance [%s]  for list view", entity.getId()), e);
    }
  }

}

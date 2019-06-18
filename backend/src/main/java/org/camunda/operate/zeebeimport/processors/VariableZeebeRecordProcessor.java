/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.processors;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.es.schema.templates.VariableTemplate;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.zeebeimport.record.value.VariableRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import static org.camunda.operate.zeebeimport.record.Intent.CREATED;
import static org.camunda.operate.zeebeimport.record.Intent.UPDATED;

@Component
public class VariableZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(VariableZeebeRecordProcessor.class);

  private static final Set<String> VARIABLE_STATES = new HashSet<>();

  static {
    VARIABLE_STATES.add(CREATED.name());
    VARIABLE_STATES.add(UPDATED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private VariableTemplate variableTemplate;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  public void processVariableRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    VariableRecordValueImpl recordValue = (VariableRecordValueImpl)record.getValue();

    //update variable
    bulkRequest.add(persistVariable(record, recordValue));

  }

  private UpdateRequest persistVariable(Record record, VariableRecordValueImpl recordValue) throws PersistenceException {
    VariableEntity entity = new VariableEntity();
    entity.setId(IdUtil.getVariableId(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getMetadata().getPartitionId());
    entity.setScopeId(IdUtil.getId(recordValue.getScopeKey(), record));
    entity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    entity.setName(recordValue.getName());
    entity.setValue(recordValue.getValue());
    return getVariableQuery(entity);
  }

  private UpdateRequest getVariableQuery(VariableEntity entity) throws PersistenceException {
    try {

      //complete operation
      batchOperationWriter.completeUpdateVariableOperation(entity.getWorkflowInstanceId(), entity.getScopeId(), entity.getName());

      logger.debug("Variable instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(VariableTemplate.VALUE, entity.getValue());

      return new UpdateRequest(variableTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert variable instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert variable instance [%s]  for list view", entity.getId()), e);
    }
  }

}

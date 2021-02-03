/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v100.processors;

import static io.zeebe.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.tasklist.entities.VariableEntity;
import io.zeebe.tasklist.es.schema.indices.VariableIndex;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.zeebeimport.v100.record.Intent;
import io.zeebe.tasklist.zeebeimport.v100.record.value.VariableRecordValueImpl;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableZeebeRecordProcessor.class);

  private static final Set<String> VARIABLE_STATES = new HashSet<>();

  static {
    VARIABLE_STATES.add(Intent.CREATED.name());
    VARIABLE_STATES.add(Intent.UPDATED.name());
  }

  @Autowired private ObjectMapper objectMapper;

  @Autowired private VariableIndex variableIndex;

  public void processVariableRecord(Record record, BulkRequest bulkRequest)
      throws PersistenceException {
    final VariableRecordValueImpl recordValue = (VariableRecordValueImpl) record.getValue();

    // update variable
    bulkRequest.add(persistVariable(record, recordValue));
  }

  private UpdateRequest persistVariable(Record record, VariableRecordValueImpl recordValue)
      throws PersistenceException {
    final VariableEntity entity = new VariableEntity();
    entity.setId(
        VariableEntity.getIdBy(String.valueOf(recordValue.getScopeKey()), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setScopeFlowNodeId(String.valueOf(recordValue.getScopeKey()));
    entity.setWorkflowInstanceId(String.valueOf(recordValue.getWorkflowInstanceKey()));
    entity.setName(recordValue.getName());
    entity.setValue(recordValue.getValue());
    return getVariableQuery(entity);
  }

  private UpdateRequest getVariableQuery(VariableEntity entity) throws PersistenceException {
    try {
      LOGGER.debug("Variable instance for list view: id {}", entity.getId());
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(VariableIndex.VALUE, entity.getValue());

      return new UpdateRequest(
              variableIndex.getIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert variable instance [%s]  for list view",
              entity.getId()),
          e);
    }
  }
}

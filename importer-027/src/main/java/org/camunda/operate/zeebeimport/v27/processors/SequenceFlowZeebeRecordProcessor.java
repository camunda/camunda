/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v27.processors;

import java.io.IOException;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.v27.record.Intent;
import org.camunda.operate.zeebeimport.v27.record.value.WorkflowInstanceRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;

@Component
public class SequenceFlowZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(SequenceFlowZeebeRecordProcessor.class);
  private static final String ID_PATTERN = "%s_%s";

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private SequenceFlowTemplate sequenceFlowTemplate;

  public void processSequenceFlowRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    if (intentStr.equals(Intent.SEQUENCE_FLOW_TAKEN.name())) {
      WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();
      persistSequenceFlow(record, recordValue, bulkRequest);
    }
  }

  private void persistSequenceFlow(Record record, WorkflowInstanceRecordValueImpl recordValue, BulkRequest bulkRequest) throws PersistenceException {
    SequenceFlowEntity entity = new SequenceFlowEntity();
    entity.setId(String.format(ID_PATTERN, recordValue.getWorkflowInstanceKey(), recordValue.getElementId()));
    entity.setWorkflowInstanceKey(recordValue.getWorkflowInstanceKey());
    entity.setActivityId(recordValue.getElementId());
    bulkRequest.add(getSequenceFlowInsertQuery(entity));
  }

  private IndexRequest getSequenceFlowInsertQuery(SequenceFlowEntity sequenceFlow) throws PersistenceException {
    try {
      logger.debug("Index sequence flow: id {}", sequenceFlow.getId());
      return new IndexRequest(sequenceFlowTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, sequenceFlow.getId())
        .source(objectMapper.writeValueAsString(sequenceFlow), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to index sequence flow", e);
      throw new PersistenceException(String.format("Error preparing the query to index sequence flow [%s]", sequenceFlow), e);
    }
  }

}

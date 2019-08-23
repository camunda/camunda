/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.processors;

import java.io.IOException;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.es.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.record.Intent;
import org.camunda.operate.zeebeimport.record.value.WorkflowInstanceRecordValueImpl;
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
    entity.setId( ConversionUtils.toStringOrNull(record.getKey()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setWorkflowInstanceKey(recordValue.getWorkflowInstanceKey());
    entity.setActivityId(recordValue.getElementId());
    bulkRequest.add(getSequenceFlowInsertQuery(entity));
  }

  private IndexRequest getSequenceFlowInsertQuery(SequenceFlowEntity sequenceFlow) throws PersistenceException {
    try {
      logger.debug("Index sequence flow: id {}", sequenceFlow.getId());
      return new IndexRequest(sequenceFlowTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, sequenceFlow.getId())
        .source(objectMapper.writeValueAsString(sequenceFlow), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to index sequence flow", e);
      throw new PersistenceException(String.format("Error preparing the query to index sequence flow [%s]", sequenceFlow), e);
    }
  }

}

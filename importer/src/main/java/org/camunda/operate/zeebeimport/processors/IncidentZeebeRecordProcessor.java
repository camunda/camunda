/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.processors;

import java.io.IOException;
import java.time.Instant;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.ElasticsearchManager;
import org.camunda.operate.zeebeimport.record.value.IncidentRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import static org.camunda.operate.zeebeimport.record.Intent.CREATED;
import static org.camunda.operate.zeebeimport.record.Intent.RESOLVED;

@Component
public class IncidentZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(IncidentZeebeRecordProcessor.class);

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private ElasticsearchManager elasticsearchManager;

  public void processIncidentRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

    persistIncident(record, recordValue, bulkRequest);

  }

  private void persistIncident(Record record, IncidentRecordValueImpl recordValue, BulkRequest bulkRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final Long incidentKey = record.getKey();
    if (intentStr.equals(RESOLVED.toString())) {

      //resolve corresponding operation
      //TODO must be idempotent
      //not possible to include UpdateByQueryRequestBuilder in bulk query -> executing at once
      elasticsearchManager.completeOperation(recordValue.getWorkflowInstanceKey(), incidentKey, OperationType.RESOLVE_INCIDENT);
      //if we update smth, we need it to have affect at once
      bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

      bulkRequest.add(getIncidentDeleteQuery(incidentKey));
    } else if (intentStr.equals(CREATED.toString())) {
      IncidentEntity incident = new IncidentEntity();
      incident.setId( ConversionUtils.toStringOrNull(incidentKey));
      incident.setKey(incidentKey);
      if (recordValue.getJobKey() > 0) {
        incident.setJobKey(recordValue.getJobKey());
      }
      if (recordValue.getWorkflowInstanceKey() > 0) {
        incident.setWorkflowInstanceKey(recordValue.getWorkflowInstanceKey());
      }
      if (recordValue.getWorkflowKey() > 0) {
        incident.setWorkflowKey(recordValue.getWorkflowKey());
      }
      incident.setErrorMessage(StringUtils.trimWhitespace(recordValue.getErrorMessage()));
      incident.setErrorType(recordValue.getErrorType());
      incident.setFlowNodeId(recordValue.getElementId());
      if (recordValue.getElementInstanceKey() > 0) {
        incident.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
      }
      incident.setState(IncidentState.ACTIVE);
      incident.setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      bulkRequest.add(getIncidentInsertQuery(incident));
    }
  }

  private IndexRequest getIncidentInsertQuery(IncidentEntity incident) throws PersistenceException {
    try {
      logger.debug("Index incident: id {}", incident.getId());
      return new IndexRequest(incidentTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, String.valueOf(incident.getKey()))
        .source(objectMapper.writeValueAsString(incident), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to index incident", e);
      throw new PersistenceException(String.format("Error preparing the query to index incident [%s]", incident), e);
    }
  }

  private DeleteRequest getIncidentDeleteQuery(Long incidentKey) throws PersistenceException {
    logger.debug("Delete incident: key {}", incidentKey);
    return new DeleteRequest(incidentTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, incidentKey.toString());
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.processors;

import java.io.IOException;
import org.camunda.operate.entities.ErrorType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.zeebeimport.record.value.IncidentRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.exporter.record.Record;
import static org.camunda.operate.zeebeimport.record.Intent.CREATED;
import static org.camunda.operate.zeebeimport.record.Intent.RESOLVED;

@Component
public class IncidentZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(IncidentZeebeRecordProcessor.class);

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TransportClient esClient;

  @Autowired
  private IncidentTemplate incidentTemplate;

  public void processIncidentRecord(Record record, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

    persistIncident(record, recordValue, bulkRequestBuilder);

  }

  private void persistIncident(Record record, IncidentRecordValueImpl recordValue, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    final String intentStr = record.getMetadata().getIntent().name();
    if (intentStr.equals(RESOLVED.toString())) {
      bulkRequestBuilder.add(getIncidentDeleteQuery(IdUtil.getId(record.getKey(), record)));
    } else if (intentStr.equals(CREATED.toString())) {
      IncidentEntity incident = new IncidentEntity();
      incident.setId(IdUtil.getId(record.getKey(), record));
      incident.setKey(record.getKey());
      if (recordValue.getJobKey() > 0) {
        incident.setJobKey(recordValue.getJobKey());
      }
      if (recordValue.getWorkflowInstanceKey() > 0) {
        incident.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
      }
      incident.setErrorMessage(recordValue.getErrorMessage());
      incident.setErrorType(ErrorType.createFrom(recordValue.getErrorType()));
      incident.setFlowNodeId(recordValue.getElementId());
      if (recordValue.getElementInstanceKey() > 0) {
        incident.setFlowNodeInstanceId(IdUtil.getId(recordValue.getElementInstanceKey(), record));
      }
      incident.setState(IncidentState.ACTIVE);
      incident.setCreationTime(DateUtil.toOffsetDateTime(record.getTimestamp()));
      bulkRequestBuilder.add(getIncidentInsertQuery(incident));
    }
  }

  private IndexRequestBuilder getIncidentInsertQuery(IncidentEntity incident) throws PersistenceException {
    try {
      logger.debug("Index incident: id {}", incident.getId());
      return esClient
        .prepareIndex(incidentTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, incident.getId())
        .setSource(objectMapper.writeValueAsString(incident), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to index incident", e);
      throw new PersistenceException(String.format("Error preparing the query to index incident [%s]", incident), e);
    }
  }

  private DeleteRequestBuilder getIncidentDeleteQuery(String incidentId) throws PersistenceException {
    logger.debug("Delete incident: id {}", incidentId);
    return esClient.prepareDelete(incidentTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, incidentId);
  }

}

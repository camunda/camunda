/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_1.processors;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.zeebeimport.ElasticsearchManager;
import io.camunda.operate.zeebeimport.IncidentNotifier;
import io.camunda.operate.zeebeimport.v1_1.record.value.IncidentRecordValueImpl;
import io.camunda.operate.zeebeimport.v1_1.record.Intent;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.Record;

@Component
public class IncidentZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(IncidentZeebeRecordProcessor.class);

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private ElasticsearchManager elasticsearchManager;


  @Autowired
  private IncidentNotifier incidentNotifier;

  public void processIncidentRecord(List<Record> records, BulkRequest bulkRequest) throws PersistenceException {
    List<IncidentEntity> newIncidents = new ArrayList<>();
    for (Record record: records) {
      processIncidentRecord(record, bulkRequest, newIncidents::add);
    }
    if (operateProperties.getAlert().getWebhook() != null) {
      incidentNotifier.notifyOnIncidents(newIncidents);
    }
  }

  public void processIncidentRecord(Record record, BulkRequest bulkRequest,
      Consumer<IncidentEntity> newIncidentHandler) throws PersistenceException {
    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

    persistIncident(record, recordValue, bulkRequest, newIncidentHandler);

  }

  private void persistIncident(Record record, IncidentRecordValueImpl recordValue, BulkRequest bulkRequest,
      Consumer<IncidentEntity> newIncidentHandler) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final Long incidentKey = record.getKey();
    if (intentStr.equals(Intent.RESOLVED.toString())) {

      //resolve corresponding operation
      elasticsearchManager.completeOperation(null, recordValue.getProcessInstanceKey(), incidentKey, OperationType.RESOLVE_INCIDENT, bulkRequest);

      bulkRequest.add(getIncidentDeleteQuery(incidentKey));
    } else if (intentStr.equals(Intent.CREATED.toString())) {
      IncidentEntity incident = new IncidentEntity();
      incident.setId( ConversionUtils.toStringOrNull(incidentKey));
      incident.setKey(incidentKey);
      if (recordValue.getJobKey() > 0) {
        incident.setJobKey(recordValue.getJobKey());
      }
      if (recordValue.getProcessInstanceKey() > 0) {
        incident.setProcessInstanceKey(recordValue.getProcessInstanceKey());
      }
      if (recordValue.getProcessDefinitionKey() > 0) {
        incident.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
      }
      String errorMessage = StringUtils.trimWhitespace(recordValue.getErrorMessage());
      incident.setErrorMessage(errorMessage);
      incident.setErrorType(ErrorType.fromZeebeErrorType(recordValue.getErrorType() == null ? null : recordValue.getErrorType().name()));
      incident.setFlowNodeId(recordValue.getElementId());
      if (recordValue.getElementInstanceKey() > 0) {
        incident.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
      }
      incident.setState(IncidentState.ACTIVE);
      incident.setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      bulkRequest.add(getIncidentInsertQuery(incident));
      newIncidentHandler.accept(incident);
    }
  }

  private IndexRequest getIncidentInsertQuery(IncidentEntity incident) throws PersistenceException {
    try {
      logger.debug("Index incident: id {}", incident.getId());
      return new IndexRequest(incidentTemplate.getFullQualifiedName()).id(String.valueOf(incident.getKey()))
        .source(objectMapper.writeValueAsString(incident), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to index incident", e);
      throw new PersistenceException(String.format("Error preparing the query to index incident [%s]", incident), e);
    }
  }

  private DeleteRequest getIncidentDeleteQuery(Long incidentKey) throws PersistenceException {
    logger.debug("Delete incident: key {}", incidentKey);
    return new DeleteRequest().index(incidentTemplate.getFullQualifiedName()).id(incidentKey.toString());
  }

}

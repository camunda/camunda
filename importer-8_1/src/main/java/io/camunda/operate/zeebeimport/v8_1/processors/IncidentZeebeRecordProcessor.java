/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_1.processors;


import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.zeebeimport.ElasticsearchQueries;
import io.camunda.operate.zeebeimport.IncidentNotifier;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
  private OperationsManager operationsManager;

  @Autowired
  private ElasticsearchQueries elasticsearchQueries;

  @Autowired
  private IncidentNotifier incidentNotifier;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private RestHighLevelClient esClient;

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
    IncidentRecordValue recordValue = (IncidentRecordValue)record.getValue();

    persistIncident(record, recordValue, bulkRequest, newIncidentHandler);

  }

  private void persistIncident(Record record, IncidentRecordValue recordValue,
      BulkRequest bulkRequest,
      Consumer<IncidentEntity> newIncidentHandler) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final Long incidentKey = record.getKey();
    if (intentStr.equals(IncidentIntent.RESOLVED.toString())) {

      //resolve corresponding operation
      operationsManager.completeOperation(null, recordValue.getProcessInstanceKey(), incidentKey, OperationType.RESOLVE_INCIDENT, bulkRequest);

      bulkRequest.add(getIncidentUpdateQuery(incidentKey, IncidentState.RESOLVED));
    } else if (intentStr.equals(IncidentIntent.CREATED.toString())) {
      IncidentEntity incident = new IncidentEntity();
      incident.setId( ConversionUtils.toStringOrNull(incidentKey));
      incident.setKey(incidentKey);
      incident.setPartitionId(record.getPartitionId());
      if (recordValue.getJobKey() > 0) {
        incident.setJobKey(recordValue.getJobKey());
      }
      if (recordValue.getProcessInstanceKey() > 0) {
        incident.setProcessInstanceKey(recordValue.getProcessInstanceKey());
      }
      if (recordValue.getProcessDefinitionKey() > 0) {
        incident.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
      }
      incident.setBpmnProcessId(recordValue.getBpmnProcessId());
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

  private UpdateRequest getIncidentUpdateQuery(Long incidentKey, IncidentState state) throws PersistenceException {
    logger.debug("Resolve incident: key {}", incidentKey);
    Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(IncidentTemplate.PENDING, true);
    updateFields.put(IncidentTemplate.STATE, state);
    return new UpdateRequest(incidentTemplate.getFullQualifiedName(), String.valueOf(incidentKey))
        .doc(updateFields)
        .retryOnConflict(UPDATE_RETRY_COUNT);
  }

}

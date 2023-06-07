/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_3.processors;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
  private PostImporterQueueTemplate postImporterQueueTemplate;

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

    persistPostImportQueueEntry(record, recordValue, bulkRequest);

  }

  private void persistPostImportQueueEntry(Record record, IncidentRecordValue recordValue, BulkRequest bulkRequest)
      throws PersistenceException {
    String intent = record.getIntent().name();
    PostImporterQueueEntity postImporterQueueEntity = new PostImporterQueueEntity()
        //id = incident key + intent
        .setId(String.format("%d-%s", record.getKey(), intent))
        .setActionType(PostImporterActionType.INCIDENT)
        .setIntent(intent)
        .setKey(record.getKey())
        .setPosition(record.getPosition())
        .setCreationTime(OffsetDateTime.now())
        .setPartitionId(record.getPartitionId())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey());
    try {
      bulkRequest.add(
          new IndexRequest(postImporterQueueTemplate.getFullQualifiedName()).id(postImporterQueueEntity.getId())
              .source(objectMapper.writeValueAsString(postImporterQueueEntity), XContentType.JSON));
    } catch (JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to index post import queue entry [%s]", postImporterQueueEntity), e);
    }
  }

  private void persistIncident(Record record, IncidentRecordValue recordValue,
      BulkRequest bulkRequest,
      Consumer<IncidentEntity> newIncidentHandler) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final Long incidentKey = record.getKey();
    if (intentStr.equals(IncidentIntent.RESOLVED.toString())) {

      //resolve corresponding operation
      operationsManager.completeOperation(null, recordValue.getProcessInstanceKey(), incidentKey,
          OperationType.RESOLVE_INCIDENT, bulkRequest);
      //resolved incident is not updated directly, only in post importer
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
      incident.setState(IncidentState.PENDING);
      incident.setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));

      bulkRequest.add(getIncidentInsertQuery(incident));
      newIncidentHandler.accept(incident);
    }
  }


  private UpdateRequest getIncidentInsertQuery(IncidentEntity incident) throws PersistenceException {
    try {
      logger.debug("Index incident: id {}", incident.getId());
      //we only insert incidents but never update -> update will be performed in post importer
      return new UpdateRequest().index(incidentTemplate.getFullQualifiedName()).id(String.valueOf(incident.getKey()))
          .doc(new HashMap<>())   //empty
          .upsert(objectMapper.writeValueAsString(incident), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to index incident", e);
      throw new PersistenceException(String.format("Error preparing the query to index incident [%s]", incident), e);
    }
  }

}

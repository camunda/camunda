/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_2.processors;


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
import io.camunda.operate.zeebeimport.UpdateIncidentsFromProcessInstancesAction;
import io.camunda.operate.zeebeimport.util.TreePath;
import io.camunda.operate.zeebeimport.v1_2.record.Intent;
import io.camunda.operate.zeebeimport.v1_2.record.value.IncidentRecordValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
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

  @Autowired
  private BeanFactory beanFactory;

  public Callable<Void> processIncidentRecord(List<Record> records, BulkRequest bulkRequest) throws PersistenceException {
    List<IncidentEntity> newIncidents = new ArrayList<>();
    List<String> processInstanceIdsForTreePathUpdate = new ArrayList<>();
    for (Record record: records) {
      processIncidentRecord(record, bulkRequest, newIncidents::add, processInstanceIdsForTreePathUpdate);
    }
    if (operateProperties.getAlert().getWebhook() != null) {
      incidentNotifier.notifyOnIncidents(newIncidents);
    }
    return beanFactory.getBean(UpdateIncidentsFromProcessInstancesAction.class,
        processInstanceIdsForTreePathUpdate);
  }

  public void processIncidentRecord(Record record, BulkRequest bulkRequest,
      Consumer<IncidentEntity> newIncidentHandler,
      List<String> processInstanceIdsForTreePathUpdate) throws PersistenceException {
    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

    persistIncident(record, recordValue, bulkRequest, newIncidentHandler,
        processInstanceIdsForTreePathUpdate);

  }

  private void persistIncident(Record record, IncidentRecordValueImpl recordValue, BulkRequest bulkRequest,
      Consumer<IncidentEntity> newIncidentHandler,
      List<String> processInstanceIdsForTreePathUpdate) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final Long incidentKey = record.getKey();
    if (intentStr.equals(Intent.RESOLVED.toString())) {

      //resolve corresponding operation
      operationsManager.completeOperation(null, recordValue.getProcessInstanceKey(), incidentKey, OperationType.RESOLVE_INCIDENT, bulkRequest);

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
      if (incident.getTreePath() == null) {
        String processInstanceTreePath = elasticsearchQueries
            .findProcessInstanceTreePath(recordValue.getProcessInstanceKey());
        if (processInstanceTreePath == null) {
          logger.warn("No tree path found for incident [{}], processInstanceKey [{}]",
              incident.getKey(), recordValue.getProcessInstanceKey());
          final String treePath = new TreePath().startTreePath(
              String.valueOf(recordValue.getProcessInstanceKey()))
              .appendFlowNode(incident.getFlowNodeId())
              .appendFlowNodeInstance(String.valueOf(incident.getFlowNodeInstanceKey())).toString();
          incident.setTreePath(treePath);
          processInstanceIdsForTreePathUpdate.add(String.valueOf(recordValue.getProcessInstanceKey()));
        } else {
          incident.setTreePath(new TreePath(processInstanceTreePath)
              .appendFlowNode(incident.getFlowNodeId())
              .appendFlowNodeInstance(String.valueOf(incident.getFlowNodeInstanceKey()))
              .toString());
        }
      }
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

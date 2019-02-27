/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.HashMap;
import java.util.Map;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.EventTemplate;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Configuration
public class ElasticsearchRequestCreatorsHolder {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchRequestCreatorsHolder.class);

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TransportClient esClient;

  @Autowired
  private WorkflowIndex workflowType;

  @Autowired
  private EventTemplate eventTemplate;

  @Autowired
  private IncidentTemplate workflowInstanceTemplate;

  @Autowired
  private SequenceFlowTemplate sequenceFlowTemplate;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  /**
   * Insert Workflow.
   * @return
   */
  public ElasticsearchRequestCreator<WorkflowEntity> workflowEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {
        logger.debug("Workflow: id {}, bpmnProcessId {}", entity.getId(), entity.getBpmnProcessId());

        //find workflow instances with empty workflow name and version
        //FIXME
//        final List<String> workflowInstanceIds = workflowInstanceReader.queryWorkflowInstancesWithEmptyWorkflowVersion(entity.getKey());
//        for (String workflowInstanceId : workflowInstanceIds) {
//          Map<String, Object> updateFields = new HashMap<>();
//          updateFields.put(IncidentTemplate.WORKFLOW_NAME, entity.getName());
//          updateFields.put(IncidentTemplate.WORKFLOW_VERSION, entity.getVersion());
//          bulkRequestBuilder.add(esClient
//            .prepareUpdate(workflowInstanceTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, workflowInstanceId)
//            .setDoc(updateFields));
//        }

        return bulkRequestBuilder.add(
          esClient
            .prepareIndex(workflowType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
            .setSource(objectMapper.writeValueAsString(entity), XContentType.JSON)
        );
      } catch (JsonProcessingException e) {
        logger.error("Error preparing the query to insert workflow", e);
        throw new PersistenceException(String.format("Error preparing the query to insert workflow [%s]", entity.getId()), e);
      }
    };
  }

  public ElasticsearchRequestCreator<EventEntity> eventEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {

        logger.debug("Event: id {}, eventSourceType {}, eventType {}, workflowInstanceId {}",
          entity.getId(), entity.getEventSourceType(), entity.getEventType(), entity.getWorkflowInstanceId());

        //write event
        bulkRequestBuilder =
          bulkRequestBuilder.add(esClient.prepareIndex(eventTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
          .setSource(objectMapper.writeValueAsString(entity), XContentType.JSON));

        //complete operation in workflow instance if needed
        if (entity.getEventSourceType().equals(EventSourceType.JOB) &&
          entity.getEventType().equals(org.camunda.operate.entities.EventType.RETRIES_UPDATED)) {
          //TODO must be idempotent
          //not possible to include UpdateByQueryRequestBuilder in bulk query -> executing at once
          batchOperationWriter.completeOperation(entity.getWorkflowInstanceId(), OperationType.UPDATE_JOB_RETRIES);
          //if we update smth, we need it to have affect at once
          bulkRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        } else if (entity.getEventSourceType().equals(EventSourceType.WORKFLOW_INSTANCE) &&
          entity.getEventType().equals(org.camunda.operate.entities.EventType.ELEMENT_TERMINATED)){
          //TODO must be idempotent
          //not possible to include UpdateByQueryRequestBuilder in bulk query -> executing at once
          batchOperationWriter.completeOperation(entity.getWorkflowInstanceId(), OperationType.CANCEL_WORKFLOW_INSTANCE);
          //if we update smth, we need it to have affect at once
          bulkRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        }
        return bulkRequestBuilder;
      } catch (JsonProcessingException e) {
        logger.error("Error preparing the query to insert event", e);
        throw new PersistenceException(String.format("Error preparing the query to insert event [%s]", entity.getId()), e);
      }
    };
  }

  @Bean
  public Map<Class<? extends OperateEntity>, ElasticsearchRequestCreator> getEsRequestMapping() {
    Map<Class<? extends OperateEntity>, ElasticsearchRequestCreator> map = new HashMap<>();
    map.put(WorkflowEntity.class, workflowEsRequestCreator());
    map.put(EventEntity.class, eventEsRequestCreator());
    return map;
  }

}

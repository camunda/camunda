/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v100.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.zeebe.protocol.record.value.deployment.ResourceType;
import io.zeebe.tasklist.entities.WorkflowEntity;
import io.zeebe.tasklist.es.schema.indices.WorkflowIndex;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.util.ConversionUtils;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.zeebeimport.util.XMLUtil;
import io.zeebe.tasklist.zeebeimport.v100.record.value.DeploymentRecordValueImpl;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowZeebeRecordProcessor.class);

  private static final Set<String> STATES = new HashSet<>();

  static {
    STATES.add(DeploymentIntent.CREATED.name());
  }

  @Autowired private ObjectMapper objectMapper;

  @Autowired private WorkflowIndex workflowIndex;

  @Autowired private XMLUtil xmlUtil;

  public void processDeploymentRecord(Record record, BulkRequest bulkRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();

    if (STATES.contains(intentStr)) {
      final DeploymentRecordValueImpl recordValue = (DeploymentRecordValueImpl) record.getValue();
      final Map<String, DeploymentResource> resources = resourceToMap(recordValue.getResources());
      for (DeployedWorkflow workflow : recordValue.getDeployedWorkflows()) {
        persistWorkflow(workflow, resources, bulkRequest);
      }
    }
  }

  private void persistWorkflow(
      DeployedWorkflow workflow, Map<String, DeploymentResource> resources, BulkRequest bulkRequest)
      throws PersistenceException {
    final String resourceName = workflow.getResourceName();
    final DeploymentResource resource = resources.get(resourceName);

    final WorkflowEntity workflowEntity = createEntity(workflow, resource);
    LOGGER.debug("Workflow: key {}", workflowEntity.getKey());

    try {
      bulkRequest.add(
          new IndexRequest(
                  workflowIndex.getIndexName(),
                  ElasticsearchUtil.ES_INDEX_TYPE,
                  ConversionUtils.toStringOrNull(workflowEntity.getKey()))
              .source(objectMapper.writeValueAsString(workflowEntity), XContentType.JSON));
    } catch (JsonProcessingException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to insert workflow [%s]", workflowEntity.getKey()),
          e);
    }
  }

  private WorkflowEntity createEntity(DeployedWorkflow workflow, DeploymentResource resource) {
    final WorkflowEntity workflowEntity = new WorkflowEntity();

    workflowEntity.setId(String.valueOf(workflow.getWorkflowKey()));
    workflowEntity.setKey(workflow.getWorkflowKey());

    final ResourceType resourceType = resource.getResourceType();
    if (ResourceType.BPMN_XML.equals(resourceType)) {
      final byte[] byteArray = resource.getResource();

      final Optional<WorkflowEntity> diagramData = xmlUtil.extractDiagramData(byteArray);
      if (diagramData.isPresent()) {
        workflowEntity.setName(diagramData.get().getName());
        workflowEntity.setFlowNodes(diagramData.get().getFlowNodes());
      }
    }

    return workflowEntity;
  }

  private Map<String, DeploymentResource> resourceToMap(List<DeploymentResource> resources) {
    return resources.stream()
        .collect(Collectors.toMap(DeploymentResource::getResourceName, Function.identity()));
  }
}

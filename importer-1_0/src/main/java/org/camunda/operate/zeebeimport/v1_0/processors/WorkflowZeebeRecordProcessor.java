/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v1_0.processors;

import javax.xml.parsers.SAXParserFactory;
import static org.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.schema.indices.WorkflowIndex;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.ElasticsearchManager;
import org.camunda.operate.zeebeimport.util.XMLUtil;
import org.camunda.operate.zeebeimport.v1_0.record.value.DeploymentRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.zeebe.protocol.record.value.deployment.ResourceType;

@Component
public class WorkflowZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowZeebeRecordProcessor.class);

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private final static Set<String> STATES = new HashSet<>();
  static {
    STATES.add(DeploymentIntent.CREATED.name());
  }

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private ElasticsearchManager elasticsearchManager;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private WorkflowIndex workflowIndex;

  @Autowired
  private SAXParserFactory saxParserFactory;

  @Autowired
  private XMLUtil xmlUtil;

  public void processDeploymentRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();

    if (STATES.contains(intentStr)) {
      DeploymentRecordValueImpl recordValue = (DeploymentRecordValueImpl)record.getValue();
      Map<String, DeploymentResource> resources = resourceToMap(recordValue.getResources());
      for (DeployedWorkflow workflow : recordValue.getDeployedWorkflows()) {
        persistWorkflow(workflow, resources, record, bulkRequest);
      }
    }

  }

  private void persistWorkflow(DeployedWorkflow workflow, Map<String, DeploymentResource> resources, Record record, BulkRequest bulkRequest) throws PersistenceException {
    String resourceName = workflow.getResourceName();
    DeploymentResource resource = resources.get(resourceName);

    final WorkflowEntity workflowEntity = createEntity(workflow, resource);
    logger.debug("Workflow: key {}, bpmnProcessId {}", workflowEntity.getKey(), workflowEntity.getBpmnProcessId());

    try {
      updateFieldsInInstancesFor(workflowEntity, bulkRequest);

      bulkRequest.add(new IndexRequest(workflowIndex.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, ConversionUtils.toStringOrNull(workflowEntity.getKey()))
          .source(objectMapper.writeValueAsString(workflowEntity), XContentType.JSON)
      );
    } catch (JsonProcessingException e) {
      logger.error("Error preparing the query to insert workflow", e);
      throw new PersistenceException(String.format("Error preparing the query to insert workflow [%s]", workflowEntity.getKey()), e);
    }
  }

  private void updateFieldsInInstancesFor(final WorkflowEntity workflowEntity, BulkRequest bulkRequest) {
    List<Long> workflowInstanceKeys = elasticsearchManager.queryWorkflowInstancesWithEmptyWorkflowVersion(workflowEntity.getKey());
    for (Long workflowInstanceKey : workflowInstanceKeys) {
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.WORKFLOW_NAME, workflowEntity.getName());
      updateFields.put(ListViewTemplate.WORKFLOW_VERSION, workflowEntity.getVersion());
      UpdateRequest updateRequest = new UpdateRequest(listViewTemplate.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, workflowInstanceKey.toString())
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT);
      bulkRequest.add(updateRequest);
    }
  }

  private WorkflowEntity createEntity(DeployedWorkflow workflow, DeploymentResource resource) {
    WorkflowEntity workflowEntity = new WorkflowEntity();

    workflowEntity.setId(String.valueOf(workflow.getWorkflowKey()));
    workflowEntity.setKey(workflow.getWorkflowKey());
    workflowEntity.setBpmnProcessId(workflow.getBpmnProcessId());
    workflowEntity.setVersion(workflow.getVersion());

    ResourceType resourceType = resource.getResourceType();
    if (resourceType != null && resourceType.equals(ResourceType.BPMN_XML)) {
      byte[] byteArray = resource.getResource();

      String bpmn = new String(byteArray, CHARSET);
      workflowEntity.setBpmnXml(bpmn);

      String resourceName = resource.getResourceName();
      workflowEntity.setResourceName(resourceName);

      final Optional<WorkflowEntity> diagramData = xmlUtil.extractDiagramData(byteArray);
      if(diagramData.isPresent()) {
        workflowEntity.setName(diagramData.get().getName());
      }
    }

    return workflowEntity;
  }

  private Map<String, DeploymentResource> resourceToMap(List<DeploymentResource> resources) {
    return resources.stream().collect(Collectors.toMap(DeploymentResource::getResourceName, Function.identity()));
  }

}

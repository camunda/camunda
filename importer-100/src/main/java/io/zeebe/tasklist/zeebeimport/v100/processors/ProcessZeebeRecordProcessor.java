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
import io.zeebe.protocol.record.value.deployment.DeployedProcess;
import io.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.zeebe.protocol.record.value.deployment.ResourceType;
import io.zeebe.tasklist.entities.FormEntity;
import io.zeebe.tasklist.entities.ProcessEntity;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.schema.indices.FormIndex;
import io.zeebe.tasklist.schema.indices.ProcessIndex;
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
public class ProcessZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessZeebeRecordProcessor.class);

  private static final Set<String> STATES = new HashSet<>();

  static {
    STATES.add(DeploymentIntent.CREATED.name());
  }

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ProcessIndex processIndex;

  @Autowired private FormIndex formIndex;

  @Autowired private XMLUtil xmlUtil;

  public void processDeploymentRecord(Record record, BulkRequest bulkRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();

    if (STATES.contains(intentStr)) {
      final DeploymentRecordValueImpl recordValue = (DeploymentRecordValueImpl) record.getValue();
      final Map<String, DeploymentResource> resources = resourceToMap(recordValue.getResources());
      for (DeployedProcess process : recordValue.getDeployedProcesses()) {
        persistProcess(process, resources, bulkRequest);
      }
    }
  }

  private void persistProcess(
      DeployedProcess process, Map<String, DeploymentResource> resources, BulkRequest bulkRequest)
      throws PersistenceException {
    final String resourceName = process.getResourceName();
    final DeploymentResource resource = resources.get(resourceName);

    final ProcessEntity processEntity = createEntity(process, resource);
    LOGGER.debug("Process: key {}", processEntity.getKey());

    try {
      bulkRequest.add(
          new IndexRequest(
                  processIndex.getFullQualifiedName(),
                  ElasticsearchUtil.ES_INDEX_TYPE,
                  ConversionUtils.toStringOrNull(processEntity.getKey()))
              .source(objectMapper.writeValueAsString(processEntity), XContentType.JSON));
    } catch (JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert process [%s]", processEntity.getKey()),
          e);
    }
  }

  private ProcessEntity createEntity(DeployedProcess process, DeploymentResource resource) {
    final ProcessEntity processEntity = new ProcessEntity();

    processEntity.setId(String.valueOf(process.getProcessDefinitionKey()));
    processEntity.setKey(process.getProcessDefinitionKey());

    final ResourceType resourceType = resource.getResourceType();
    if (ResourceType.BPMN_XML.equals(resourceType)) {
      final byte[] byteArray = resource.getResource();

      final Optional<ProcessEntity> diagramData = xmlUtil.extractDiagramData(byteArray);
      if (diagramData.isPresent()) {
        processEntity.setName(diagramData.get().getName());
        processEntity.setFlowNodes(diagramData.get().getFlowNodes());
      }
    }

    return processEntity;
  }

  private Map<String, DeploymentResource> resourceToMap(List<DeploymentResource> resources) {
    return resources.stream()
        .collect(Collectors.toMap(DeploymentResource::getResourceName, Function.identity()));
  }

  // TODO for future use
  private void persistForm(
      long processDefinitionKey, String formKey, String schema, BulkRequest bulkRequest)
      throws PersistenceException {
    final FormEntity formEntity =
        new FormEntity(String.valueOf(processDefinitionKey), formKey, schema);
    LOGGER.debug("Form: key {}", formKey);
    try {
      bulkRequest.add(
          new IndexRequest(
                  formIndex.getFullQualifiedName(),
                  ElasticsearchUtil.ES_INDEX_TYPE,
                  ConversionUtils.toStringOrNull(formEntity.getId()))
              .source(objectMapper.writeValueAsString(formEntity), XContentType.JSON));
    } catch (JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert task form [%s]", formEntity.getId()),
          e);
    }
  }
}

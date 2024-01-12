/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v850.processors.es;

import static io.camunda.tasklist.util.ConversionUtils.toStringOrNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.zeebeimport.common.ProcessDefinitionDeletionProcessor;
import io.camunda.tasklist.zeebeimport.util.XMLUtil;
import io.camunda.tasklist.zeebeimport.v850.record.value.deployment.DeployedProcessImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessZeebeRecordProcessorElasticSearch.class);

  private static final Set<String> STATES_TO_PERSIST = Set.of(ProcessIntent.CREATED.name());
  private static final Set<String> STATES_TO_DELETE = Set.of(ProcessIntent.DELETED.name());

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ProcessIndex processIndex;

  @Autowired private FormIndex formIndex;

  @Autowired private XMLUtil xmlUtil;

  @Autowired private ProcessDefinitionDeletionProcessor processDefinitionDeletionProcessor;

  public void processDeploymentRecord(Record<DeployedProcessImpl> record, BulkRequest bulkRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final DeployedProcessImpl recordValue = record.getValue();
    final String processDefinitionKey = String.valueOf(record.getValue().getProcessDefinitionKey());
    if (STATES_TO_PERSIST.contains(intentStr)) {
      final Map<String, String> userTaskForms = new HashMap<>();
      persistProcess(recordValue, bulkRequest, userTaskForms::put);

      final List<PersistenceException> exceptions = new ArrayList<>();
      userTaskForms.forEach(
          (formKey, schema) -> {
            try {
              persistForm(
                  processDefinitionKey, formKey, schema, bulkRequest, recordValue.getTenantId());
            } catch (PersistenceException e) {
              exceptions.add(e);
            }
          });
      if (!exceptions.isEmpty()) {
        throw exceptions.get(0);
      }
    } else if (STATES_TO_DELETE.contains(intentStr)) {
      bulkRequest.add(
          processDefinitionDeletionProcessor.createProcessDefinitionDeleteRequests(
              processDefinitionKey, DeleteRequest::new));
    }
  }

  private void persistProcess(
      Process process, BulkRequest bulkRequest, BiConsumer<String, String> userTaskFormCollector)
      throws PersistenceException {

    final ProcessEntity processEntity = createEntity(process, userTaskFormCollector);
    LOGGER.debug("Process: key {}", processEntity.getKey());

    try {
      bulkRequest.add(
          new IndexRequest()
              .index(processIndex.getFullQualifiedName())
              .id(toStringOrNull(processEntity.getKey()))
              .source(objectMapper.writeValueAsString(processEntity), XContentType.JSON));
    } catch (JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert process [%s]", processEntity.getKey()),
          e);
    }
  }

  private ProcessEntity createEntity(
      Process process, BiConsumer<String, String> userTaskFormCollector) {
    final ProcessEntity processEntity = new ProcessEntity();

    processEntity.setId(String.valueOf(process.getProcessDefinitionKey()));
    processEntity.setKey(process.getProcessDefinitionKey());
    processEntity.setBpmnProcessId(process.getBpmnProcessId());
    processEntity.setVersion(process.getVersion());
    processEntity.setTenantId(process.getTenantId());

    final byte[] byteArray = process.getResource();

    xmlUtil.extractDiagramData(
        byteArray,
        processEntity::setName,
        flowNode -> processEntity.getFlowNodes().add(flowNode),
        userTaskFormCollector,
        processEntity::setFormKey,
        formId -> processEntity.setFormId(formId),
        processEntity::setStartedByForm);

    Optional.ofNullable(processEntity.getFormKey())
        .ifPresent(key -> processEntity.setIsFormEmbedded(true));

    Optional.ofNullable(processEntity.getFormId())
        .ifPresent(
            id -> {
              processEntity.setIsFormEmbedded(false);
            });

    return processEntity;
  }

  private void persistForm(
      String processDefinitionKey,
      String formKey,
      String schema,
      BulkRequest bulkRequest,
      String tenantId)
      throws PersistenceException {
    final FormEntity formEntity = new FormEntity(processDefinitionKey, formKey, schema, tenantId);
    LOGGER.debug("Form: key {}", formKey);
    try {
      bulkRequest.add(
          new IndexRequest()
              .index(formIndex.getFullQualifiedName())
              .id(toStringOrNull(formEntity.getId()))
              .source(objectMapper.writeValueAsString(formEntity), XContentType.JSON));

    } catch (JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert task form [%s]", formEntity.getId()),
          e);
    }
  }
}

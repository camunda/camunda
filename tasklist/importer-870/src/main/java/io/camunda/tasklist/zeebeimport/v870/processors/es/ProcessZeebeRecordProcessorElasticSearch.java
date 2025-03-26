/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.es;

import static io.camunda.tasklist.util.ConversionUtils.toStringOrNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.zeebeimport.common.ProcessDefinitionDeletionProcessor;
import io.camunda.tasklist.zeebeimport.util.XMLUtil;
import io.camunda.tasklist.zeebeimport.v870.record.value.deployment.DeployedProcessImpl;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.tasklist.FormEntity;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ProcessZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessZeebeRecordProcessorElasticSearch.class);

  private static final Set<String> STATES_TO_PERSIST = Set.of(ProcessIntent.CREATED.name());
  private static final Set<String> STATES_TO_DELETE = Set.of(ProcessIntent.DELETED.name());

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("tasklistProcessIndex")
  private ProcessIndex processIndex;

  @Autowired private FormIndex formIndex;

  @Autowired private XMLUtil xmlUtil;

  @Autowired private ProcessDefinitionDeletionProcessor processDefinitionDeletionProcessor;

  public void processDeploymentRecord(
      final Record<DeployedProcessImpl> record, final BulkRequest bulkRequest)
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
            } catch (final PersistenceException e) {
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
      final Process process,
      final BulkRequest bulkRequest,
      final BiConsumer<String, String> userTaskFormCollector)
      throws PersistenceException {

    final var processEntity = createEntity(process, userTaskFormCollector);
    LOGGER.debug("Process: key {}", processEntity.getKey());

    try {
      bulkRequest.add(
          new IndexRequest()
              .index(processIndex.getFullQualifiedName())
              .id(toStringOrNull(processEntity.getKey()))
              .source(objectMapper.writeValueAsString(processEntity), XContentType.JSON));
    } catch (final JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert process [%s]", processEntity.getKey()),
          e);
    }
  }

  private ProcessEntity createEntity(
      final Process process, final BiConsumer<String, String> userTaskFormCollector) {
    final ProcessEntity processEntity = new ProcessEntity();

    processEntity.setId(String.valueOf(process.getProcessDefinitionKey()));
    processEntity.setKey(process.getProcessDefinitionKey());
    processEntity.setBpmnProcessId(process.getBpmnProcessId());
    processEntity.setVersion(process.getVersion());
    processEntity.setTenantId(process.getTenantId());
    processEntity.setBpmnXml(new String(process.getResource()));

    final byte[] byteArray = process.getResource();

    xmlUtil.extractDiagramData(
        byteArray,
        process.getBpmnProcessId()::equals,
        processEntity::setName,
        flowNode -> processEntity.getFlowNodes().add(flowNode),
        userTaskFormCollector,
        processEntity::setFormKey,
        formId -> processEntity.setFormId(formId),
        processEntity::setIsPublic);

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
      final String processDefinitionKey,
      final String formKey,
      final String schema,
      final BulkRequest bulkRequest,
      final String tenantId)
      throws PersistenceException {
    final var id = String.format("%s_%s", processDefinitionKey, formKey);
    final FormEntity formEntity =
        new FormEntity()
            .setId(id)
            .setTenantId(tenantId)
            .setFormId(formKey)
            .setProcessDefinitionId(processDefinitionKey)
            .setSchema(schema)
            .setEmbedded(true)
            .setIsDeleted(false);
    LOGGER.debug("Form: key {}", formKey);
    try {
      bulkRequest.add(
          new IndexRequest()
              .index(formIndex.getFullQualifiedName())
              .id(toStringOrNull(formEntity.getId()))
              .source(objectMapper.writeValueAsString(formEntity), XContentType.JSON));

    } catch (final JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert task form [%s]", formEntity.getId()),
          e);
    }
  }
}

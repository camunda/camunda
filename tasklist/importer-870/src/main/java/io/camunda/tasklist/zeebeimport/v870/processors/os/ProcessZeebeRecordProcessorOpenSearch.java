/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.zeebeimport.common.ProcessDefinitionDeletionProcessor;
import io.camunda.tasklist.zeebeimport.util.XMLUtil;
import io.camunda.tasklist.zeebeimport.v870.record.value.deployment.DeployedProcessImpl;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.form.FormEntity;
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
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ProcessZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessZeebeRecordProcessorOpenSearch.class);

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
      final Record<DeployedProcessImpl> record, final List<BulkOperation> operations)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final String processDefinitionKey = String.valueOf(record.getValue().getProcessDefinitionKey());

    if (STATES_TO_PERSIST.contains(intentStr)) {
      final DeployedProcessImpl recordValue = record.getValue();

      final Map<String, String> userTaskForms = new HashMap<>();
      persistProcess(recordValue, operations, userTaskForms::put);
      final List<PersistenceException> exceptions = new ArrayList<>();
      userTaskForms.forEach(
          (formKey, schema) -> {
            try {
              persistForm(
                  processDefinitionKey, formKey, schema, recordValue.getTenantId(), operations);
            } catch (final PersistenceException e) {
              exceptions.add(e);
            }
          });
      if (!exceptions.isEmpty()) {
        throw exceptions.get(0);
      }
    } else if (STATES_TO_DELETE.contains(intentStr)) {
      operations.addAll(
          processDefinitionDeletionProcessor.createProcessDefinitionDeleteRequests(
              processDefinitionKey,
              (index, id) ->
                  new BulkOperation.Builder()
                      .delete(DeleteOperation.of(del -> del.index(index).id(id)))
                      .build()));
    }
  }

  private void persistProcess(
      final Process process,
      final List<BulkOperation> operations,
      final BiConsumer<String, String> userTaskFormCollector) {

    final var processEntity = createEntity(process, userTaskFormCollector);
    LOGGER.debug("Process: key {}", processEntity.getKey());

    operations.add(
        new BulkOperation.Builder()
            .index(
                IndexOperation.of(
                    io ->
                        io.index(processIndex.getFullQualifiedName())
                            .id(ConversionUtils.toStringOrNull(processEntity.getKey()))
                            .document(CommonUtils.getJsonObjectFromEntity(processEntity))))
            .build());
  }

  private ProcessEntity createEntity(
      final Process process, final BiConsumer<String, String> userTaskFormCollector) {
    final ProcessEntity processEntity =
        new ProcessEntity()
            .setId(String.valueOf(process.getProcessDefinitionKey()))
            .setKey(process.getProcessDefinitionKey())
            .setBpmnProcessId(process.getBpmnProcessId())
            .setVersion(process.getVersion())
            .setTenantId(process.getTenantId())
            .setBpmnXml(new String(process.getResource()));

    final byte[] byteArray = process.getResource();

    xmlUtil.extractDiagramData(
        byteArray,
        process.getBpmnProcessId()::equals,
        processEntity::setName,
        flowNode -> processEntity.getFlowNodes().add(flowNode),
        userTaskFormCollector,
        processEntity::setFormKey,
        processEntity::setFormId,
        processEntity::setIsPublic);

    Optional.ofNullable(processEntity.getFormKey())
        .ifPresent(key -> processEntity.setIsFormEmbedded(true));

    Optional.ofNullable(processEntity.getFormId())
        .ifPresent(id -> processEntity.setIsFormEmbedded(false));

    return processEntity;
  }

  private void persistForm(
      final String processDefinitionKey,
      final String formKey,
      final String schema,
      final String tenantId,
      final List<BulkOperation> operations)
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

    operations.add(
        new BulkOperation.Builder()
            .index(
                IndexOperation.of(
                    io ->
                        io.index(formIndex.getFullQualifiedName())
                            .id(ConversionUtils.toStringOrNull(formEntity.getId()))
                            .document(CommonUtils.getJsonObjectFromEntity(formEntity))))
            .build());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v840.processors.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.zeebeimport.util.XMLUtil;
import io.camunda.tasklist.zeebeimport.v840.record.value.deployment.DeployedProcessImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.*;
import java.util.function.BiConsumer;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessZeebeRecordProcessorOpenSearch.class);

  private static final Set<String> STATES = new HashSet<>();

  static {
    STATES.add(ProcessIntent.CREATED.name());
  }

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ProcessIndex processIndex;

  @Autowired private FormIndex formIndex;

  @Autowired private XMLUtil xmlUtil;

  public void processDeploymentRecord(
      Record<DeployedProcessImpl> record, List<BulkOperation> operations)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();

    if (STATES.contains(intentStr)) {
      final DeployedProcessImpl recordValue = record.getValue();

      final Map<String, String> userTaskForms = new HashMap<>();
      persistProcess(recordValue, operations, userTaskForms::put);

      final List<PersistenceException> exceptions = new ArrayList<>();
      userTaskForms.forEach(
          (formKey, schema) -> {
            try {
              persistForm(
                  recordValue.getProcessDefinitionKey(),
                  formKey,
                  schema,
                  recordValue.getTenantId(),
                  operations);
            } catch (PersistenceException e) {
              exceptions.add(e);
            }
          });
      if (!exceptions.isEmpty()) {
        throw exceptions.get(0);
      }
    }
  }

  private void persistProcess(
      Process process,
      List<BulkOperation> operations,
      BiConsumer<String, String> userTaskFormCollector) {

    final ProcessEntity processEntity = createEntity(process, userTaskFormCollector);
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
      Process process, BiConsumer<String, String> userTaskFormCollector) {
    final ProcessEntity processEntity =
        new ProcessEntity()
            .setId(String.valueOf(process.getProcessDefinitionKey()))
            .setKey(process.getProcessDefinitionKey())
            .setBpmnProcessId(process.getBpmnProcessId())
            .setVersion(process.getVersion())
            .setTenantId(process.getTenantId());

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
      long processDefinitionKey,
      String formKey,
      String schema,
      String tenantId,
      List<BulkOperation> operations)
      throws PersistenceException {
    final FormEntity formEntity =
        new FormEntity(String.valueOf(processDefinitionKey), formKey, schema, tenantId);
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

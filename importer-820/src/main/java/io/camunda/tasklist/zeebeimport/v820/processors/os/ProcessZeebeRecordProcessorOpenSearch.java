/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v820.processors.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.zeebeimport.util.XMLUtil;
import io.camunda.tasklist.zeebeimport.v820.record.value.deployment.DeployedProcessImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  public void processDeploymentRecord(Record record, List<BulkOperation> operations)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();

    if (STATES.contains(intentStr)) {
      final DeployedProcessImpl recordValue = (DeployedProcessImpl) record.getValue();

      final Map<String, String> userTaskForms = new HashMap<>();
      persistProcess(
          recordValue, operations, (formKey, schema) -> userTaskForms.put(formKey, schema));

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
      BiConsumer<String, String> userTaskFormCollector)
      throws PersistenceException {

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
    final ProcessEntity processEntity = new ProcessEntity();

    processEntity.setId(String.valueOf(process.getProcessDefinitionKey()));
    processEntity.setKey(process.getProcessDefinitionKey());
    processEntity.setBpmnProcessId(process.getBpmnProcessId());
    processEntity.setVersion(process.getVersion());

    final byte[] byteArray = process.getResource();

    xmlUtil.extractDiagramData(
        byteArray,
        name -> processEntity.setName(name),
        flowNode -> processEntity.getFlowNodes().add(flowNode),
        userTaskFormCollector,
        formKey -> processEntity.setFormKey(formKey),
        formId -> processEntity.setFormId(formId),
        startedByForm -> processEntity.setStartedByForm(startedByForm));

    return processEntity;
  }

  private Map<String, DeploymentResource> resourceToMap(List<DeploymentResource> resources) {
    return resources.stream()
        .collect(Collectors.toMap(DeploymentResource::getResourceName, Function.identity()));
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

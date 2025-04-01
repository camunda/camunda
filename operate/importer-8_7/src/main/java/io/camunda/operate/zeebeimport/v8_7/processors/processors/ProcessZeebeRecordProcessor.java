/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_7.processors.processors;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.zeebeimport.util.XMLUtil;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ProcessZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessZeebeRecordProcessor.class);

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private static final Set<String> STATES = new HashSet<>();

  static {
    STATES.add(ProcessIntent.CREATED.name());
  }

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private ListViewStore listViewStore;

  @Autowired
  @Qualifier("operateProcessIndex")
  private ProcessIndex processIndex;

  @Autowired private XMLUtil xmlUtil;

  public void processDeploymentRecord(final Record record, final BatchRequest batchRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();

    if (STATES.contains(intentStr)) {
      final ProcessMetadataValue recordValue = (ProcessMetadataValue) record.getValue();
      persistProcess((Process) recordValue, batchRequest);
    }
  }

  private void persistProcess(final Process process, final BatchRequest batchRequest)
      throws PersistenceException {
    final ProcessEntity processEntity = createEntity(process);
    LOGGER.debug(
        "Process: key {}, bpmnProcessId {}",
        processEntity.getKey(),
        processEntity.getBpmnProcessId());
    updateFieldsInInstancesFor(processEntity, batchRequest);
    batchRequest.addWithId(
        processIndex.getFullQualifiedName(),
        ConversionUtils.toStringOrNull(processEntity.getKey()),
        processEntity);
  }

  private void updateFieldsInInstancesFor(
      final ProcessEntity processEntity, final BatchRequest batchRequest)
      throws PersistenceException {
    final List<Long> processInstanceKeys =
        listViewStore.getProcessInstanceKeysWithEmptyProcessVersionFor(processEntity.getKey());
    for (final Long processInstanceKey : processInstanceKeys) {
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.PROCESS_NAME, processEntity.getName());
      updateFields.put(ListViewTemplate.PROCESS_VERSION, processEntity.getVersion());
      updateFields.put(ListViewTemplate.PROCESS_VERSION_TAG, processEntity.getVersion());
      batchRequest.update(
          listViewTemplate.getFullQualifiedName(), processInstanceKey.toString(), updateFields);
    }
  }

  private ProcessEntity createEntity(final Process process) {
    final ProcessEntity processEntity =
        new ProcessEntity()
            .setId(String.valueOf(process.getProcessDefinitionKey()))
            .setKey(process.getProcessDefinitionKey())
            .setBpmnProcessId(process.getBpmnProcessId())
            .setVersion(process.getVersion())
            .setTenantId(tenantOrDefault(process.getTenantId()));

    final byte[] byteArray = process.getResource();

    final String bpmn = new String(byteArray, CHARSET);
    processEntity.setBpmnXml(bpmn);

    final String resourceName = process.getResourceName();
    processEntity.setResourceName(resourceName);

    if (!process.getVersionTag().isEmpty()) {
      processEntity.setVersionTag(process.getVersionTag());
    }

    final Optional<ProcessEntity> diagramData =
        xmlUtil.extractDiagramData(byteArray, process.getBpmnProcessId());
    if (diagramData.isPresent()) {
      processEntity
          .setName(diagramData.get().getName())
          .setFlowNodes(diagramData.get().getFlowNodes());
    }
    return processEntity;
  }
}

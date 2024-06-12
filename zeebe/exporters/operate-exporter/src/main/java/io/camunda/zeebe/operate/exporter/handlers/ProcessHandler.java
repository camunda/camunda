/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.operate.exporter.util.XMLUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ProcessHandler implements ExportHandler<ProcessEntity, Process> {

  private static final Set<Intent> STATES = new HashSet<>();

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  static {
    STATES.add(ProcessIntent.CREATED);
  }

  private final ProcessIndex processIndex;

  private final XMLUtil xmlUtil;

  public ProcessHandler(final ProcessIndex processIndex, final XMLUtil xmlUtil) {
    this.processIndex = processIndex;
    this.xmlUtil = xmlUtil;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS;
  }

  @Override
  public Class<ProcessEntity> getEntityType() {
    return ProcessEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<Process> record) {
    return STATES.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<Process> record) {
    return List.of(String.valueOf(record.getValue().getProcessDefinitionKey()));
  }

  @Override
  public ProcessEntity createNewEntity(final String id) {
    return new ProcessEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<Process> record, final ProcessEntity entity) {
    final Process process = record.getValue();
    entity
        .setKey(process.getProcessDefinitionKey())
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setTenantId(tenantOrDefault(process.getTenantId()));
    final byte[] byteArray = process.getResource();

    final String bpmn = new String(byteArray, CHARSET);
    entity.setBpmnXml(bpmn);

    final String resourceName = process.getResourceName();
    entity.setResourceName(resourceName);

    final Optional<ProcessEntity> diagramData =
        xmlUtil.extractDiagramData(byteArray, process.getBpmnProcessId());
    if (diagramData.isPresent()) {
      entity.setName(diagramData.get().getName()).setFlowNodes(diagramData.get().getFlowNodes());
    }
  }

  @Override
  public void flush(final ProcessEntity entity, final NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(getIndexName(), entity);
  }

  @Override
  public String getIndexName() {
    return processIndex.getFullQualifiedName();
  }
}

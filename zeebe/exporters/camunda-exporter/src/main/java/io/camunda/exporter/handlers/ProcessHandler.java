/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.ProcessFlowNodeEntity;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessHandler implements ExportHandler<ProcessEntity, Process> {

  private final String indexName;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public ProcessHandler(
      final String indexName, final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.indexName = indexName;
    this.processCache = processCache;
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
    return record.getIntent().equals(ProcessIntent.CREATED);
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
        .setId(String.valueOf(process.getProcessDefinitionKey()))
        .setKey(process.getProcessDefinitionKey())
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setTenantId(ExporterUtil.tenantOrDefault(process.getTenantId()));
    final byte[] byteArray = process.getResource();

    final String bpmn = new String(byteArray, StandardCharsets.UTF_8);
    entity.setBpmnXml(bpmn);

    final String resourceName = process.getResourceName();
    entity.setResourceName(resourceName);

    final var versionTag = process.getVersionTag();
    if (!ExporterUtil.isEmpty(versionTag)) {
      entity.setVersionTag(versionTag);
    }

    extractProcessModelData(process, entity);

    // update local cache so that the process info is available immediately to the process instance
    // record handler
    final var cachedProcessEntity =
        new CachedProcessEntity(
            entity.getName(),
            entity.getVersionTag(),
            Integer.toString(entity.getVersion()),
            entity.getCallActivityIds(),
            getFlowNodesMap(entity.getFlowNodes()));
    processCache.put(process.getProcessDefinitionKey(), cachedProcessEntity);
  }

  @Override
  public void flush(final ProcessEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private void extractProcessModelData(final Process process, final ProcessEntity entity) {
    ProcessModelReader.of(process.getResource(), process.getBpmnProcessId())
        .ifPresent(
            processModelReader -> {
              entity.setName(processModelReader.extractProcessName());
              entity.setIsPublic(processModelReader.extractIsPublicAccess());
              entity.setFlowNodes(
                  processModelReader.extractFlowNodes().stream()
                      .map(fn -> new ProcessFlowNodeEntity(fn.getId(), fn.getName()))
                      .toList());
              entity.setCallActivityIds(
                  ProcessCacheUtil.sortedCallActivityIds(
                      processModelReader.extractCallActivities()));
              processModelReader
                  .extractStartFormLink()
                  .ifPresent(
                      formLink -> {
                        entity.setFormId(formLink.formId());
                        entity.setFormKey(formLink.formKey());
                        entity.setIsFormEmbedded(!ExporterUtil.isEmpty(formLink.formKey()));
                      });
            });
  }

  private static Map<String, String> getFlowNodesMap(final List<ProcessFlowNodeEntity> flowNodes) {
    final Map<String, String> flowNodesMap = new HashMap<>();
    for (final ProcessFlowNodeEntity flowNode : flowNodes) {
      flowNodesMap.put(flowNode.getId(), flowNode.getName());
    }

    return flowNodesMap;
  }
}

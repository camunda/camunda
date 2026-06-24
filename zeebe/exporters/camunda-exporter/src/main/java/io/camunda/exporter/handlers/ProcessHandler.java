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
import io.camunda.zeebe.exporter.common.extensionproperty.ExtensionPropertyConfiguration;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ProcessHandler implements ExportHandler<ProcessEntity, Process> {

  private final String indexName;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;
  private final ExtensionPropertyConfiguration extensionPropertiesConfiguration;

  public ProcessHandler(
      final String indexName,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache,
      final ExtensionPropertyConfiguration extensionPropertiesConfiguration) {
    this.indexName = indexName;
    this.processCache = processCache;
    this.extensionPropertiesConfiguration = extensionPropertiesConfiguration;
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

    final var reader = getProcessModelReader(process);
    if (reader != null) {
      extractProcessModelData(reader, entity);
    }

    // update local cache so that the process info is available immediately to the process instance
    // record handler
    final var cachedProcessEntity =
        ProcessCacheUtil.createCachedProcessEntity(
            entity.getName(),
            entity.getVersion(),
            entity.getVersionTag(),
            reader,
            extensionPropertiesConfiguration);

    // add extracted flow nodes data from cached entity to avoid reading model nodes multiple times
    entity.setFlowNodes(
        cachedProcessEntity.flowNodesMap().entrySet().stream()
            .map(e -> new ProcessFlowNodeEntity(e.getKey(), e.getValue()))
            .toList());
    entity.setCallActivityIds(cachedProcessEntity.callElementIds());

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

  private ProcessModelReader getProcessModelReader(final Process process) {
    return ProcessModelReader.of(process.getResource(), process.getBpmnProcessId()).orElse(null);
  }

  private void extractProcessModelData(
      final ProcessModelReader reader, final ProcessEntity entity) {
    entity.setName(reader.extractProcessName());
    entity.setIsPublic(reader.extractIsPublicAccess());
    reader
        .extractStartFormLink()
        .ifPresent(
            formLink -> {
              entity.setFormId(formLink.formId());
              entity.setFormKey(formLink.formKey());
              entity.setIsFormEmbedded(!ExporterUtil.isEmpty(formLink.formKey()));
            });
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.service.ProcessDefinitionWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import io.camunda.zeebe.util.modelreader.ProcessModelReader.StartFormLink;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class ProcessExportHandler implements RdbmsExportHandler<Process> {

  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public ProcessExportHandler(
      final ProcessDefinitionWriter processDefinitionWriter,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.processDefinitionWriter = processDefinitionWriter;
    this.processCache = processCache;
  }

  @Override
  public boolean canExport(final Record<Process> record) {
    // do not react on ProcessEvent.DELETED to keep historic data
    return record.getValueType() == ValueType.PROCESS
        && record.getIntent() == ProcessIntent.CREATED;
  }

  @Override
  public void export(final Record<Process> record) {
    final var value = record.getValue();
    final var processModelReader =
        ProcessModelReader.of(value.getResource(), value.getBpmnProcessId()).orElse(null);

    final var dbModel = map(value, processModelReader);
    processDefinitionWriter.create(dbModel);

    final CachedProcessEntity cachedProcessEntity;
    if (processModelReader != null) {
      final var callActivities =
          ProcessCacheUtil.sortedCallActivityIds(processModelReader.extractCallActivities());
      final var flowNodes = processModelReader.extractFlowNodes();
      final var flowNodesMap = ProcessCacheUtil.getFlowNodesMap(flowNodes);
      final var hasUserTasks = ProcessModelReader.hasUserTasks(flowNodes);
      final var extensionProperties = ProcessModelReader.extractExtensionProperties(flowNodes);
      cachedProcessEntity =
          new CachedProcessEntity(
              dbModel.name(),
              dbModel.version(),
              dbModel.versionTag(),
              callActivities,
              flowNodesMap,
              hasUserTasks,
              extensionProperties);
    } else {
      cachedProcessEntity =
          new CachedProcessEntity(
              dbModel.name(),
              dbModel.version(),
              dbModel.versionTag(),
              List.of(),
              Map.of(),
              true,
              Map.of());
    }
    processCache.put(value.getProcessDefinitionKey(), cachedProcessEntity);
  }

  private ProcessDefinitionDbModel map(final Process value, final ProcessModelReader reader) {
    String processName = null;
    String formId = null;
    if (reader != null) {
      processName = reader.extractProcessName();
      formId = reader.extractStartFormLink().map(StartFormLink::formId).orElse(null);
    }

    return new ProcessDefinitionDbModel(
        value.getProcessDefinitionKey(),
        value.getBpmnProcessId(),
        value.getResourceName(),
        processName,
        value.getTenantId(),
        StringUtils.defaultIfEmpty(value.getVersionTag(), null),
        value.getVersion(),
        new String(value.getResource(), StandardCharsets.UTF_8),
        formId);
  }
}

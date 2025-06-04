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
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessExportHandler implements RdbmsExportHandler<Process> {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessExportHandler.class);

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
    final Process value = record.getValue();
    processDefinitionWriter.create(map(value));
    final String resourceName = value.getResourceName();
    final var versionTag = value.getVersionTag();
    final var processModelReader =
        ProcessModelReader.of(value.getResource(), value.getBpmnProcessId());
    processModelReader.ifPresent(
        reader -> {
          final var activities =
              ProcessCacheUtil.sortedCallActivityIds(reader.extractCallActivities());
          final var flowNodesMap = ProcessCacheUtil.getFlowNodesMap(reader.extractFlowNodes());
          final var cachedProcessEntity =
              new CachedProcessEntity(resourceName, versionTag, activities, flowNodesMap);
          processCache.put(value.getProcessDefinitionKey(), cachedProcessEntity);
        });
  }

  private ProcessDefinitionDbModel map(final Process value) {
    final Optional<ProcessModelReader> processModelReader =
        ProcessModelReader.of(value.getResource(), value.getBpmnProcessId());

    String processName = null;
    String formId = null;
    if (processModelReader.isPresent()) {
      final var reader = processModelReader.get();
      processName = reader.extractProcessName();
      formId = reader.extractStartFormLink().map(StartFormLink::formId).orElse(null);
    } else {
      LOG.warn("Failed to read process model for process with key '{}'", value.getBpmnProcessId());
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

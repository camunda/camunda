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
import io.camunda.operate.zeebeimport.util.XMLUtil;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessExportHandler implements RdbmsExportHandler<Process> {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessExportHandler.class);

  private final ProcessDefinitionWriter processDefinitionWriter;

  public ProcessExportHandler(final ProcessDefinitionWriter processDefinitionWriter) {
    this.processDefinitionWriter = processDefinitionWriter;
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
  }

  private ProcessDefinitionDbModel map(final Process value) {
    Optional<ProcessEntity> processEntity = Optional.empty();

    try {
      processEntity =
          new XMLUtil().extractDiagramData(value.getResource(), value.getBpmnProcessId());
    } catch (final Exception e) {
      // skip
      LOG.warn(
          "Unable to parse XML diagram for process {}: {}",
          value.getBpmnProcessId(),
          e.getMessage());
    }

    return new ProcessDefinitionDbModel(
        value.getProcessDefinitionKey(),
        value.getBpmnProcessId(),
        value.getResourceName(),
        processEntity.map(ProcessEntity::getName).orElse(null),
        value.getTenantId(),
        value.getVersionTag(),
        value.getVersion(),
        new String(value.getResource(), StandardCharsets.UTF_8),
        processEntity.map(ProcessEntity::getFormId).orElse(null));
  }
}

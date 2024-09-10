/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.domain.ProcessDefinitionModel;
import io.camunda.db.rdbms.service.ProcessDefinitionRdbmsService;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.operate.zeebeimport.util.XMLUtil;

public class ProcessDeploymentExportHandler implements RdbmsExportHandler<Process> {

  private final ProcessDefinitionRdbmsService processDefinitionRdbmsService;

  public ProcessDeploymentExportHandler(final ProcessDefinitionRdbmsService processDefinitionRdbmsService) {
    this.processDefinitionRdbmsService = processDefinitionRdbmsService;
  }

  @Override
  public boolean canExport(final Record<Process> record) {
    return record.getIntent() == ProcessIntent.CREATED;
  }

  @Override
  public void export(final Record<Process> record) {
    final Process value = record.getValue();
    processDefinitionRdbmsService.save(map(value));
  }

  private ProcessDefinitionModel map(final Process value) {
    var xml = new XMLUtil().extractDiagramData(value.getResource(), value.getBpmnProcessId());

    return new ProcessDefinitionModel(
        value.getProcessDefinitionKey(),
        value.getBpmnProcessId(),
        xml.map(ProcessEntity::getName).orElse(null),
        value.getTenantId(),
        value.getVersionTag(),
        value.getVersion()
    );
  }
}

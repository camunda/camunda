/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.service.ExporterPositionRdbmsService;
import io.camunda.db.rdbms.service.ProcessInstanceRdbmsService;
import io.camunda.db.rdbms.service.ProcessRdbmsService;
import io.camunda.db.rdbms.service.VariableRdbmsService;

/** A holder for all rdbms services */
public class RdbmsService {

  private final ExecutionQueue executionQueue;
  private final ExporterPositionRdbmsService exporterPositionRdbmsService;
  private final ProcessRdbmsService processRdbmsService;
  private final ProcessInstanceRdbmsService processInstanceRdbmsService;
  private final VariableRdbmsService variableRdbmsService;

  public RdbmsService(
      final ExecutionQueue executionQueue,
      final ExporterPositionRdbmsService exporterPositionRdbmsService,
      final ProcessRdbmsService processRdbmsService,
      final ProcessInstanceRdbmsService processInstanceRdbmsService,
      final VariableRdbmsService variableRdbmsService) {
    this.executionQueue = executionQueue;
    this.exporterPositionRdbmsService = exporterPositionRdbmsService;
    this.processRdbmsService = processRdbmsService;
    this.processInstanceRdbmsService = processInstanceRdbmsService;
    this.variableRdbmsService = variableRdbmsService;
  }

  public ExporterPositionRdbmsService getExporterPositionRdbmsService() {
    return exporterPositionRdbmsService;
  }

  public ProcessRdbmsService getProcessDeploymentRdbmsService() {
    return processRdbmsService;
  }

  public ProcessInstanceRdbmsService getProcessInstanceRdbmsService() {
    return processInstanceRdbmsService;
  }

  public VariableRdbmsService getVariableRdbmsService() {
    return variableRdbmsService;
  }

  public ExecutionQueue executionQueue() {
    return executionQueue;
  }
}

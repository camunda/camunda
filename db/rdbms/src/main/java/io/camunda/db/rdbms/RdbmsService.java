/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.read.service.ProcessDefinitionReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.read.service.VariableReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.service.ExporterPositionService;

/** A holder for all rdbms services */
public class RdbmsService {

  private final ExecutionQueue executionQueue;
  private final ExporterPositionService exporterPositionService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessInstanceReader processInstanceReader;
  private final VariableReader variableReader;

  public RdbmsService(
      final ExecutionQueue executionQueue,
      final ExporterPositionService exporterPositionService,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessInstanceReader processInstanceReader,
      final VariableReader variableReader) {
    this.executionQueue = executionQueue;
    this.exporterPositionService = exporterPositionService;
    this.processDefinitionReader = processDefinitionReader;
    this.processInstanceReader = processInstanceReader;
    this.variableReader = variableReader;
  }

  public ExporterPositionService getExporterPositionRdbmsService() {
    return exporterPositionService;
  }

  public ProcessDefinitionReader getProcessDefinitionReader() {
    return processDefinitionReader;
  }

  public ProcessInstanceReader getProcessInstanceReader() {
    return processInstanceReader;
  }

  public VariableReader getVariableReader() {
    return variableReader;
  }

  public RdbmsWriter createWriter() {
    return new RdbmsWriter(executionQueue);
  }
}

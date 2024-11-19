/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.read.service.FlowNodeInstanceReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.read.service.UserTaskReader;
import io.camunda.db.rdbms.read.service.VariableReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;

/** A holder for all rdbms services */
public class RdbmsService {

  private final RdbmsWriterFactory rdbmsWriterFactory;
  private final FlowNodeInstanceReader flowNodeInstanceReader;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessInstanceReader processInstanceReader;
  private final VariableReader variableReader;
  private final UserTaskReader userTaskReader;

  public RdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessInstanceReader processInstanceReader,
      final VariableReader variableReader,
      final UserTaskReader userTaskReader) {
    this.rdbmsWriterFactory = rdbmsWriterFactory;
    this.flowNodeInstanceReader = flowNodeInstanceReader;
    this.processDefinitionReader = processDefinitionReader;
    this.processInstanceReader = processInstanceReader;
    this.variableReader = variableReader;
    this.userTaskReader = userTaskReader;
  }

  public FlowNodeInstanceReader getFlowNodeInstanceReader() {
    return flowNodeInstanceReader;
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

  public UserTaskReader getUserTaskReader() {
    return userTaskReader;
  }

  public RdbmsWriter createWriter(final long partitionId) {
    return rdbmsWriterFactory.createWriter(partitionId);
  }
}

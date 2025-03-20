/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.read.service.AuthorizationReader;
import io.camunda.db.rdbms.read.service.BatchOperationReader;
import io.camunda.db.rdbms.read.service.DecisionDefinitionReader;
import io.camunda.db.rdbms.read.service.DecisionInstanceReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsReader;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceReader;
import io.camunda.db.rdbms.read.service.FormReader;
import io.camunda.db.rdbms.read.service.GroupReader;
import io.camunda.db.rdbms.read.service.IncidentReader;
import io.camunda.db.rdbms.read.service.MappingReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader;
import io.camunda.db.rdbms.read.service.RoleReader;
import io.camunda.db.rdbms.read.service.TenantReader;
import io.camunda.db.rdbms.read.service.UserReader;
import io.camunda.db.rdbms.read.service.UserTaskReader;
import io.camunda.db.rdbms.read.service.VariableReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;

/** A holder for all rdbms services */
public class RdbmsService {

  private final RdbmsWriterFactory rdbmsWriterFactory;
  private final AuthorizationReader authorizationReader;
  private final DecisionDefinitionReader decisionDefinitionReader;
  private final DecisionInstanceReader decisionInstanceReader;
  private final DecisionRequirementsReader decisionRequirementsReader;
  private final FlowNodeInstanceReader flowNodeInstanceReader;
  private final GroupReader groupReader;
  private final IncidentReader incidentReader;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessInstanceReader processInstanceReader;
  private final VariableReader variableReader;
  private final RoleReader roleReader;
  private final TenantReader tenantReader;
  private final UserReader userReader;
  private final UserTaskReader userTaskReader;
  private final FormReader formReader;
  private final MappingReader mappingReader;
  private final BatchOperationReader batchOperationReader;

  public RdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
      final AuthorizationReader authorizationReader,
      final DecisionDefinitionReader decisionDefinitionReader,
      final DecisionInstanceReader decisionInstanceReader,
      final DecisionRequirementsReader decisionRequirementsReader,
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final GroupReader groupReader,
      final IncidentReader incidentReader,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessInstanceReader processInstanceReader,
      final VariableReader variableReader,
      final RoleReader roleReader,
      final TenantReader tenantReader,
      final UserReader userReader,
      final UserTaskReader userTaskReader,
      final FormReader formReader,
      final MappingReader mappingReader,
      final BatchOperationReader batchOperationReader) {
    this.rdbmsWriterFactory = rdbmsWriterFactory;
    this.authorizationReader = authorizationReader;
    this.decisionRequirementsReader = decisionRequirementsReader;
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.decisionInstanceReader = decisionInstanceReader;
    this.flowNodeInstanceReader = flowNodeInstanceReader;
    this.groupReader = groupReader;
    this.incidentReader = incidentReader;
    this.processDefinitionReader = processDefinitionReader;
    this.processInstanceReader = processInstanceReader;
    this.tenantReader = tenantReader;
    this.variableReader = variableReader;
    this.roleReader = roleReader;
    this.userReader = userReader;
    this.userTaskReader = userTaskReader;
    this.formReader = formReader;
    this.mappingReader = mappingReader;
    this.batchOperationReader = batchOperationReader;
  }

  public AuthorizationReader getAuthorizationReader() {
    return authorizationReader;
  }

  public DecisionDefinitionReader getDecisionDefinitionReader() {
    return decisionDefinitionReader;
  }

  public DecisionInstanceReader getDecisionInstanceReader() {
    return decisionInstanceReader;
  }

  public DecisionRequirementsReader getDecisionRequirementsReader() {
    return decisionRequirementsReader;
  }

  public FlowNodeInstanceReader getFlowNodeInstanceReader() {
    return flowNodeInstanceReader;
  }

  public GroupReader getGroupReader() {
    return groupReader;
  }

  public IncidentReader getIncidentReader() {
    return incidentReader;
  }

  public ProcessDefinitionReader getProcessDefinitionReader() {
    return processDefinitionReader;
  }

  public ProcessInstanceReader getProcessInstanceReader() {
    return processInstanceReader;
  }

  public TenantReader getTenantReader() {
    return tenantReader;
  }

  public VariableReader getVariableReader() {
    return variableReader;
  }

  public RoleReader getRoleReader() {
    return roleReader;
  }

  public UserReader getUserReader() {
    return userReader;
  }

  public UserTaskReader getUserTaskReader() {
    return userTaskReader;
  }

  public FormReader getFormReader() {
    return formReader;
  }

  public MappingReader getMappingReader() {
    return mappingReader;
  }

  public BatchOperationReader getBatchOperationReader() {
    return batchOperationReader;
  }

  public RdbmsWriter createWriter(final long partitionId) { // todo fix in all itests afterwards?
    return createWriter(new RdbmsWriterConfig.Builder().partitionId((int) partitionId).build());
  }

  public RdbmsWriter createWriter(final RdbmsWriterConfig config) {
    return rdbmsWriterFactory.createWriter(config);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter.execution;

import io.camunda.client.api.response.ActivatedJob;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineServices;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.community.migration.adapter.execution.variable.VariableTyper;

/**
 * DelegateExecution implementation that can be initialized with an {@link ActivatedJob} and
 * provides all methods required for executing a JavaDelegate as part of a job worker.
 *
 * @author Falko Menge (Camunda)
 */
public class ZeebeJobDelegateExecution extends SimpleVariableScope implements DelegateExecution {

  public static final String VARIABLE_NAME_BUSINESS_KEY = "businessKey";

  private final ActivatedJob job;

  public ZeebeJobDelegateExecution(ActivatedJob job, VariableTyper variableTyper) {
    super(variableTyper.typeVariables(job.getBpmnProcessId(), job.getVariablesAsMap()));
    this.job = job;
  }

  @Override
  public String getProcessInstanceId() {
    return String.valueOf(job.getProcessInstanceKey());
  }

  @Override
  public String getProcessDefinitionId() {
    return String.valueOf(job.getProcessDefinitionKey());
  }

  @Override
  public String getCurrentActivityId() {
    return job.getElementId();
  }

  @Override
  public String getActivityInstanceId() {
    return String.valueOf(job.getElementInstanceKey());
  }

  @Override
  public String getTenantId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getId() {
    return getProcessInstanceId();
  }

  @Override
  public String getCurrentActivityName() {
    return getBpmnModelElementInstance().getName();
  }

  @Override
  public FlowElement getBpmnModelElementInstance() {
    throw new UnsupportedOperationException();
  }

  @Override
  public BpmnModelInstance getBpmnModelInstance() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ProcessEngineServices getProcessEngineServices() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ProcessEngine getProcessEngine() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getParentId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getParentActivityInstanceId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getEventName() {
    throw new UnsupportedOperationException(
        "This DelegateExecution implementation is not meant to be used for ExecutionListeners");
  }

  @Override
  public String getCurrentTransitionId() {
    throw new UnsupportedOperationException(
        "This DelegateExecution implementation is not meant to be used for ExecutionListeners");
  }

  @Override
  public DelegateExecution getProcessInstance() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DelegateExecution getSuperExecution() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCanceled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Incident createIncident(String incidentType, String configuration) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Incident createIncident(String incidentType, String configuration, String message) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void resolveIncident(String incidentId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getBusinessKey() {
    return getProcessBusinessKey();
  }

  @Override
  public String getProcessBusinessKey() {
    return (String) getVariable(VARIABLE_NAME_BUSINESS_KEY);
  }

  @Override
  public void setProcessBusinessKey(String businessKey) {
    setVariable(VARIABLE_NAME_BUSINESS_KEY, businessKey);
  }

  @Override
  public void setVariable(String variableName, Object value, String activityId) {
    throw new UnsupportedOperationException();
  }

  public ActivatedJob getJob() {
    return job;
  }
}

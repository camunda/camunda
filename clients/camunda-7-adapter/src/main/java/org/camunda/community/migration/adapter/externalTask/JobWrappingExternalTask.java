/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter.externalTask;

import io.camunda.client.api.response.ActivatedJob;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;
import org.camunda.bpm.engine.variable.value.TypedValue;

public class JobWrappingExternalTask implements ExternalTask {
  private final ActivatedJob job;
  private final Optional<String> businessKeyVariableName;

  public JobWrappingExternalTask(ActivatedJob job, Optional<String> businessKeyVariableName) {
    this.job = job;
    this.businessKeyVariableName = businessKeyVariableName;
  }

  @Override
  public String getActivityId() {
    return job.getElementId();
  }

  @Override
  public String getActivityInstanceId() {
    return String.valueOf(job.getElementInstanceKey());
  }

  @Override
  public String getErrorMessage() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getErrorDetails() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getExecutionId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getId() {
    return String.valueOf(job.getKey());
  }

  @Override
  public Date getLockExpirationTime() {
    return Date.from(Instant.ofEpochMilli(job.getDeadline()));
  }

  @Override
  public Date getCreateTime() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getProcessDefinitionId() {
    return String.valueOf(job.getProcessDefinitionKey());
  }

  @Override
  public String getProcessDefinitionKey() {
    return job.getBpmnProcessId();
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getProcessInstanceId() {
    return String.valueOf(job.getProcessInstanceKey());
  }

  @Override
  public Integer getRetries() {
    return job.getRetries();
  }

  @Override
  public String getWorkerId() {
    return job.getWorker();
  }

  @Override
  public String getTopicName() {
    return job.getType();
  }

  @Override
  public String getTenantId() {
    return job.getTenantId();
  }

  @Override
  public long getPriority() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T getVariable(String s) {
    return (T) job.getVariablesAsMap().get(s);
  }

  @Override
  public <T extends TypedValue> T getVariableTyped(String s) {
    return getAllVariablesTyped().getValueTyped(s);
  }

  @Override
  public <T extends TypedValue> T getVariableTyped(String s, boolean deserialize) {
    return getAllVariablesTyped().getValueTyped(s);
  }

  @Override
  public Map<String, Object> getAllVariables() {
    return job.getVariablesAsMap();
  }

  @Override
  public VariableMap getAllVariablesTyped() {
    return new VariableMapImpl(job.getVariablesAsMap());
  }

  @Override
  public VariableMap getAllVariablesTyped(boolean deserialize) {
    return getAllVariablesTyped();
  }

  @Override
  public String getBusinessKey() {
    return businessKeyVariableName
        .map(businessKeyVar -> (String) getVariable(businessKeyVar))
        .orElseThrow(UnsupportedOperationException::new);
  }

  @Override
  public String getExtensionProperty(String s) {
    return job.getCustomHeaders().get(s);
  }

  @Override
  public Map<String, String> getExtensionProperties() {
    return job.getCustomHeaders();
  }
}

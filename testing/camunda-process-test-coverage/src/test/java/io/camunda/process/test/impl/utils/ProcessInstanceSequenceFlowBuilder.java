/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.utils;

import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;

public class ProcessInstanceSequenceFlowBuilder implements ProcessInstanceSequenceFlow {

  private String sequenceFlowId;
  private String processInstanceKey;
  private String rootProcessInstanceKey;
  private String processDefinitionKey;
  private String processDefinitionId;
  private String elementId;
  private String tenantId;

  @Override
  public String getSequenceFlowId() {
    return sequenceFlowId;
  }

  @Override
  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public ProcessInstanceSequenceFlowBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setProcessDefinitionId(
      final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setProcessDefinitionKey(
      final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setRootProcessInstanceKey(
      final String rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setProcessInstanceKey(final String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setSequenceFlowId(final String sequenceFlowId) {
    this.sequenceFlowId = sequenceFlowId;
    return this;
  }

  public ProcessInstanceSequenceFlow build() {
    return this;
  }

  public static ProcessInstanceSequenceFlowBuilder newSequenceFlow(
      final String elementId, final long processInstanceKey) {
    return new ProcessInstanceSequenceFlowBuilder()
        .setProcessInstanceKey(String.valueOf(processInstanceKey))
        .setElementId(elementId)
        .setSequenceFlowId("sequenceFlow_" + elementId);
  }
}

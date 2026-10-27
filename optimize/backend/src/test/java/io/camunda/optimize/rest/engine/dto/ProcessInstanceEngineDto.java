/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine.dto;

public class ProcessInstanceEngineDto {

  protected String id;
  private String definitionId;
  private String businessKey;
  private String caseInstanceId;
  private boolean ended;
  private boolean suspended;
  private String tenantId;
  private String processDefinitionKey;
  private String processDefinitionVersion;

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getDefinitionId() {
    return definitionId;
  }

  public void setDefinitionId(final String definitionId) {
    this.definitionId = definitionId;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(final String businessKey) {
    this.businessKey = businessKey;
  }

  public String getCaseInstanceId() {
    return caseInstanceId;
  }

  public void setCaseInstanceId(final String caseInstanceId) {
    this.caseInstanceId = caseInstanceId;
  }

  public boolean isEnded() {
    return ended;
  }

  public void setEnded(final boolean ended) {
    this.ended = ended;
  }

  public boolean isSuspended() {
    return suspended;
  }

  public void setSuspended(final boolean suspended) {
    this.suspended = suspended;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }
}

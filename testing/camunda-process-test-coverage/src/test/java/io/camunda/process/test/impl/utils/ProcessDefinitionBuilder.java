/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.utils;

import io.camunda.client.api.search.response.ProcessDefinition;

public class ProcessDefinitionBuilder implements ProcessDefinition {

  private long processDefinitionKey;
  private String name;
  private String resourceName;
  private int version;
  private String versionTag;
  private String processDefinitionId;
  private String tenantId;
  private Boolean hasStartForm;

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessDefinitionBuilder setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public String getName() {
    return name;
  }

  public ProcessDefinitionBuilder setName(final String name) {
    this.name = name;
    return this;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  public ProcessDefinitionBuilder setResourceName(final String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  @Override
  public int getVersion() {
    return version;
  }

  public ProcessDefinitionBuilder setVersion(final int version) {
    this.version = version;
    return this;
  }

  @Override
  public String getVersionTag() {
    return versionTag;
  }

  public ProcessDefinitionBuilder setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public ProcessDefinitionBuilder setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public ProcessDefinitionBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public Boolean getHasStartForm() {
    return hasStartForm;
  }

  public ProcessDefinitionBuilder setHasStartForm(final Boolean hasStartForm) {
    this.hasStartForm = hasStartForm;
    return this;
  }

  public ProcessDefinition build() {
    return this;
  }

  public static ProcessDefinitionBuilder newProcessDefinition(final String processDefinitionId) {
    return new ProcessDefinitionBuilder().setProcessDefinitionId(processDefinitionId);
  }
}

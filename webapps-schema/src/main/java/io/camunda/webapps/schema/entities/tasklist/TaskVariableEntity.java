/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.tasklist;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public class TaskVariableEntity extends AbstractExporterEntity<TaskVariableEntity>
    implements TenantOwned {

  private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String name;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String value;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String fullValue;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isTruncated;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long scopeKey;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long processInstanceId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long position;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskJoinRelationship join;

  public TaskVariableEntity() {}

  public String getName() {
    return name;
  }

  public TaskVariableEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public TaskVariableEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public TaskVariableEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public Boolean getIsTruncated() {
    return isTruncated;
  }

  public TaskVariableEntity setIsTruncated(final Boolean truncated) {
    isTruncated = truncated;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public TaskVariableEntity setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public Long getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskVariableEntity setProcessInstanceId(final Long processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public TaskJoinRelationship getJoin() {
    return join;
  }

  public TaskVariableEntity setJoin(final TaskJoinRelationship join) {
    this.join = join;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public TaskVariableEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public TaskVariableEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Variable {

  public static final String KEY = VariableTemplate.KEY,
      PROCESS_INSTANCE_KEY = VariableTemplate.PROCESS_INSTANCE_KEY,
      SCOPE_KEY = VariableTemplate.SCOPE_KEY,
      TENANT_ID = VariableTemplate.TENANT_ID,
      NAME = VariableTemplate.NAME,
      VALUE = VariableTemplate.VALUE,
      FULL_VALUE = VariableTemplate.FULL_VALUE,
      TRUNCATED = VariableTemplate.IS_PREVIEW;

  private Long key;
  private Long processInstanceKey;
  private Long scopeKey;
  private String name;
  private String value;
  private Boolean truncated;
  private String tenantId;

  public Long getKey() {
    return key;
  }

  public Variable setKey(final Long key) {
    this.key = key;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public Variable setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public Variable setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public String getName() {
    return name;
  }

  public Variable setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public Variable setValue(final String value) {
    this.value = value;
    return this;
  }

  public Boolean getTruncated() {
    return truncated;
  }

  public Variable setTruncated(final Boolean truncated) {
    this.truncated = truncated;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Variable setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, processInstanceKey, scopeKey, name, value, truncated, tenantId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Variable variable = (Variable) o;
    return Objects.equals(key, variable.key)
        && Objects.equals(processInstanceKey, variable.processInstanceKey)
        && Objects.equals(scopeKey, variable.scopeKey)
        && Objects.equals(name, variable.name)
        && Objects.equals(value, variable.value)
        && Objects.equals(truncated, variable.truncated)
        && Objects.equals(tenantId, variable.tenantId);
  }

  @Override
  public String toString() {
    return "Variable{"
        + "key="
        + key
        + ", processInstanceKey="
        + processInstanceKey
        + ", scopeKey="
        + scopeKey
        + ", name='"
        + name
        + '\''
        + ", value='"
        + value
        + '\''
        + ", truncated="
        + truncated
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.operate.schema.templates.VariableTemplate;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Variable {

  public static final String KEY = VariableTemplate.KEY,
      PROCESS_INSTANCE_KEY = VariableTemplate.PROCESS_INSTANCE_KEY,
      SCOPE_KEY = VariableTemplate.SCOPE_KEY,
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

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Variable variable = (Variable) o;
    return Objects.equals(key, variable.key) && Objects.equals(processInstanceKey,
        variable.processInstanceKey) && Objects.equals(scopeKey, variable.scopeKey)
        && Objects.equals(name, variable.name) && Objects.equals(value,
        variable.value)
        && Objects.equals(truncated, variable.truncated);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, processInstanceKey, scopeKey, name, value, truncated);
  }

  @Override
  public String toString() {
    return "Variable{" +
        "key=" + key +
        ", processInstanceKey=" + processInstanceKey +
        ", scopeKey=" + scopeKey +
        ", name='" + name + '\'' +
        ", value='" + value + '\'' +
        ", truncated=" + truncated +
        '}';
  }
}

/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.utils;

import io.camunda.client.api.search.response.Variable;

public class VariableBuilder implements Variable {

  private Long variableKey;
  private String name;
  private String value;
  private String fullValue;
  private Long scopeKey;
  private Long processInstanceKey;
  private String tenantId;
  private Boolean isTruncated;

  @Override
  public Long getVariableKey() {
    return variableKey;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public String getFullValue() {
    return fullValue;
  }

  @Override
  public Long getScopeKey() {
    return scopeKey;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public Boolean isTruncated() {
    return isTruncated;
  }

  public VariableBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public VariableBuilder setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public VariableBuilder setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public VariableBuilder setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public VariableBuilder setValue(final String value) {
    this.value = value;
    return this;
  }

  public VariableBuilder setName(final String name) {
    this.name = name;
    return this;
  }

  public VariableBuilder setVariableKey(final Long variableKey) {
    this.variableKey = variableKey;
    return this;
  }

  public VariableBuilder setTruncated(final Boolean truncated) {
    isTruncated = truncated;
    return this;
  }

  public Variable build() {
    return this;
  }

  public static VariableBuilder newVariable(final String name, final String value) {
    return new VariableBuilder()
        .setName(name)
        .setValue(value)
        .setFullValue(value)
        .setTruncated(false);
  }
}

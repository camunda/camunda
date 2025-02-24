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
package io.camunda.process.test.impl.client;

import io.camunda.client.api.search.response.Variable;

public class VariableDto implements Variable {

  private long key;
  private long processInstanceKey;
  private long scopeKey;
  private String name;
  private String value;
  private boolean truncated;
  private String tenantId;

  public long getKey() {
    return key;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  public void setTruncated(final boolean truncated) {
    this.truncated = truncated;
  }

  @Override
  public Long getVariableKey() {
    return 0L;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  @Override
  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  @Override
  public String getFullValue() {
    return "";
  }

  @Override
  public Long getScopeKey() {
    return scopeKey;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public Boolean isTruncated() {
    return truncated;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public void setScopeKey(final long scopeKey) {
    this.scopeKey = scopeKey;
  }
}

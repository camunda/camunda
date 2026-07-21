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
package io.camunda.client.impl.search.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.VariableResult;
import io.camunda.client.protocol.rest.VariableSearchResult;
import java.time.OffsetDateTime;

public class VariableImpl implements Variable {

  private final Long variableKey;
  private final String name;
  private final String value;
  private final Long scopeKey;
  private final Long processInstanceKey;
  private final Long rootProcessInstanceKey;
  private final String tenantId;
  private final Boolean isTruncated;
  private final String updatedBy;
  private final OffsetDateTime updatedAt;
  @JsonIgnore private final JsonMapper jsonMapper;

  public VariableImpl(final VariableSearchResult item, final JsonMapper jsonMapper) {
    variableKey = ParseUtil.parseLongOrNull(item.getVariableKey());
    name = item.getName();
    value = item.getValue();
    scopeKey = ParseUtil.parseLongOrNull(item.getScopeKey());
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    rootProcessInstanceKey = ParseUtil.parseLongOrNull(item.getRootProcessInstanceKey());
    tenantId = item.getTenantId();
    isTruncated = item.getIsTruncated();
    updatedBy = item.getUpdatedBy();
    updatedAt = ParseUtil.parseOffsetDateTimeOrNull(item.getUpdatedAt());
    this.jsonMapper = jsonMapper;
  }

  public VariableImpl(final VariableResult item, final JsonMapper jsonMapper) {
    variableKey = ParseUtil.parseLongOrNull(item.getVariableKey());
    name = item.getName();
    value = item.getValue();
    scopeKey = ParseUtil.parseLongOrNull(item.getScopeKey());
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    rootProcessInstanceKey = ParseUtil.parseLongOrNull(item.getRootProcessInstanceKey());
    tenantId = item.getTenantId();
    isTruncated = false;
    updatedBy = item.getUpdatedBy();
    updatedAt = ParseUtil.parseOffsetDateTimeOrNull(item.getUpdatedAt());
    this.jsonMapper = jsonMapper;
  }

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
  public Long getScopeKey() {
    return scopeKey;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public Boolean isTruncated() {
    return isTruncated;
  }

  @Override
  public String getUpdatedBy() {
    return updatedBy;
  }

  @Override
  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public <T> T getValueAsType(final Class<T> type) {
    if (Boolean.TRUE.equals(isTruncated())) {
      throw new IllegalStateException("Cannot return truncated value as type " + type);
    }
    return jsonMapper.fromJson(value, type);
  }
}

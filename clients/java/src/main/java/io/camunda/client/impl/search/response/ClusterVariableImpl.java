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

import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.api.search.response.ClusterVariable;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.ClusterVariableResult;
import io.camunda.client.protocol.rest.ClusterVariableSearchResult;

public class ClusterVariableImpl implements ClusterVariable {

  private final String name;
  private final String value;
  private final String tenantId;
  private final ClusterVariableScope scope;
  private final Boolean isTruncated;

  public ClusterVariableImpl(final ClusterVariableResult clusterVariableResult) {
    name = clusterVariableResult.getName();
    value = clusterVariableResult.getValue();
    tenantId = clusterVariableResult.getTenantId();
    scope = EnumUtil.convert(clusterVariableResult.getScope(), ClusterVariableScope.class);
    isTruncated = false;
  }

  public ClusterVariableImpl(final ClusterVariableSearchResult clusterVariableSearchResult) {
    name = clusterVariableSearchResult.getName();
    value = clusterVariableSearchResult.getValue();
    tenantId = clusterVariableSearchResult.getTenantId();
    scope = EnumUtil.convert(clusterVariableSearchResult.getScope(), ClusterVariableScope.class);
    isTruncated = clusterVariableSearchResult.getIsTruncated();
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
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public ClusterVariableScope getScope() {
    return scope;
  }

  @Override
  public Boolean isTruncated() {
    return isTruncated;
  }
}

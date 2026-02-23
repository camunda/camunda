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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.UpdateClusterVariableResponse;
import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.ClusterVariableResult;

public class UpdateClusterVariableResponseImpl implements UpdateClusterVariableResponse {

  private String name;
  private String value;
  private ClusterVariableScope scope;
  private String tenantId;

  public UpdateClusterVariableResponse setResponse(
      final ClusterVariableResult clusterVariableResult) {
    name = clusterVariableResult.getName();
    value = clusterVariableResult.getValue();
    tenantId = clusterVariableResult.getTenantId();
    scope = EnumUtil.convert(clusterVariableResult.getScope(), ClusterVariableScope.class);
    return this;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public ClusterVariableScope getScope() {
    return scope;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }
}

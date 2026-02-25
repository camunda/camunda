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

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.client.protocol.rest.CreateProcessInstanceResult;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CreateProcessInstanceWithResultResponseImpl implements ProcessInstanceResult {

  private final JsonMapper jsonMapper;
  private final long processDefinitionKey;
  private final String bpmnProcessId;
  private final int version;
  private final long processInstanceKey;
  private final String tenantId;
  private final String variables;

  private Map<String, Object> variablesAsMap;
  private final Set<String> tags;
  private final String businessId;

  public CreateProcessInstanceWithResultResponseImpl(
      final JsonMapper jsonMapper, final CreateProcessInstanceResult response) {
    this.jsonMapper = jsonMapper;
    processDefinitionKey = Long.parseLong(response.getProcessDefinitionKey());
    bpmnProcessId = response.getProcessDefinitionId();
    version = response.getProcessDefinitionVersion();
    processInstanceKey = Long.parseLong(response.getProcessInstanceKey());
    tenantId = response.getTenantId();
    variables = jsonMapper.toJson(response.getVariables());
    tags = response.getTags();
    businessId = response.getBusinessId();
  }

  public CreateProcessInstanceWithResultResponseImpl(
      final JsonMapper jsonMapper, final CreateProcessInstanceWithResultResponse response) {
    this.jsonMapper = jsonMapper;
    processDefinitionKey = response.getProcessDefinitionKey();
    bpmnProcessId = response.getBpmnProcessId();
    version = response.getVersion();
    processInstanceKey = response.getProcessInstanceKey();
    variables = response.getVariables();
    tenantId = response.getTenantId();
    tags = Collections.unmodifiableSet(new HashSet<>(response.getTagsList()));
    businessId = response.getBusinessId();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getVariables() {
    return variables;
  }

  @Override
  public Map<String, Object> getVariablesAsMap() {
    if (variablesAsMap == null) {
      variablesAsMap = jsonMapper.fromJsonAsMap(variables);
    }
    return variablesAsMap;
  }

  @Override
  public <T> T getVariablesAsType(final Class<T> variableType) {
    return jsonMapper.fromJson(variables, variableType);
  }

  @Override
  public Object getVariable(final String name) {
    final Map<String, Object> variables = getVariablesAsMap();
    if (!variables.containsKey(name)) {
      throw new ClientException(String.format("The variable %s is not available", name));
    }
    return getVariablesAsMap().get(name);
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public Set<String> getTags() {
    return tags;
  }

  @Override
  public String getBusinessId() {
    return businessId;
  }

  @Override
  public String toString() {
    return "CreateProcessInstanceWithResultResponseImpl{"
        + "processDefinitionKey="
        + processDefinitionKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", processInstanceKey="
        + processInstanceKey
        + ", variables='"
        + variables
        + '\''
        + ", tags='"
        + tags
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", businessId='"
        + businessId
        + '\''
        + '}';
  }
}

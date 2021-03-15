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
package io.zeebe.client.impl.response;

import io.zeebe.client.api.JsonMapper;
import io.zeebe.client.api.response.ProcessInstanceResult;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import java.util.Map;

public final class CreateProcessInstanceWithResultResponseImpl implements ProcessInstanceResult {

  private final JsonMapper jsonMapper;
  private final long processDefinitionKey;
  private final String bpmnProcessId;
  private final int version;
  private final long processInstanceKey;
  private final String variables;

  public CreateProcessInstanceWithResultResponseImpl(
      final JsonMapper jsonMapper, final CreateProcessInstanceWithResultResponse response) {
    this.jsonMapper = jsonMapper;
    processDefinitionKey = response.getProcessDefinitionKey();
    bpmnProcessId = response.getBpmnProcessId();
    version = response.getVersion();
    processInstanceKey = response.getProcessInstanceKey();
    variables = response.getVariables();
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
    return jsonMapper.fromJsonAsMap(variables);
  }

  @Override
  public <T> T getVariablesAsType(final Class<T> variableType) {
    return jsonMapper.fromJson(variables, variableType);
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
        + '}';
  }
}

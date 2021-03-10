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
import io.zeebe.client.api.response.WorkflowInstanceResult;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceWithResultResponse;
import java.util.Map;

public final class CreateWorkflowInstanceWithResultResponseImpl implements WorkflowInstanceResult {

  private final JsonMapper jsonMapper;
  private final long workflowKey;
  private final String bpmnProcessId;
  private final int version;
  private final long workflowInstanceKey;
  private final String variables;

  public CreateWorkflowInstanceWithResultResponseImpl(
      final JsonMapper jsonMapper, final CreateWorkflowInstanceWithResultResponse response) {
    this.jsonMapper = jsonMapper;
    workflowKey = response.getWorkflowKey();
    bpmnProcessId = response.getBpmnProcessId();
    version = response.getVersion();
    workflowInstanceKey = response.getWorkflowInstanceKey();
    variables = response.getVariables();
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
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
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
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
    return "CreateWorkflowInstanceWithResultResponseImpl{"
        + "workflowKey="
        + workflowKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", variables='"
        + variables
        + '\''
        + '}';
  }
}

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
package io.camunda.zeebe.client.impl.response;

import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.CreateProcessInstanceResult;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;

public final class CreateProcessInstanceResponseImpl implements ProcessInstanceEvent {

  private final long processDefinitionKey;
  private final String bpmnProcessId;
  private final int version;
  private final long processInstanceKey;
  private final String tenantId;

  public CreateProcessInstanceResponseImpl(
      final GatewayOuterClass.CreateProcessInstanceResponse response) {
    processDefinitionKey = response.getProcessDefinitionKey();
    bpmnProcessId = response.getBpmnProcessId();
    version = response.getVersion();
    processInstanceKey = response.getProcessInstanceKey();
    tenantId = response.getTenantId();
  }

  public CreateProcessInstanceResponseImpl(final CreateProcessInstanceResult response) {
    processDefinitionKey = ParseUtil.parseLongOrEmpty(response.getProcessDefinitionKey());
    bpmnProcessId = response.getProcessDefinitionId();
    version = response.getProcessDefinitionVersion();
    processInstanceKey = ParseUtil.parseLongOrEmpty(response.getProcessInstanceKey());
    tenantId = response.getTenantId();
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
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String toString() {
    return "CreateProcessInstanceResponseImpl{"
        + "processDefinitionKey="
        + processDefinitionKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", processInstanceKey="
        + processInstanceKey
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}

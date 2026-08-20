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

import io.camunda.client.api.response.Process;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessMetadata;
import java.util.Objects;

public final class ProcessImpl implements Process {

  private final long processDefinitionKey;
  private final String bpmnProcessId;
  private final int version;
  private final String resourceName;
  private final String tenantId;

  public ProcessImpl(final ProcessMetadata process) {
    this(
        process.getProcessDefinitionKey(),
        process.getBpmnProcessId(),
        process.getVersion(),
        process.getResourceName(),
        process.getTenantId());
  }

  public ProcessImpl(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final int version,
      final String resourceName,
      final String tenantId) {
    this.processDefinitionKey = processDefinitionKey;
    this.bpmnProcessId = bpmnProcessId;
    this.version = version;
    this.resourceName = resourceName;
    this.tenantId = tenantId;
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
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(processDefinitionKey, bpmnProcessId, version, resourceName, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessImpl process = (ProcessImpl) o;
    return processDefinitionKey == process.processDefinitionKey
        && version == process.version
        && Objects.equals(bpmnProcessId, process.bpmnProcessId)
        && Objects.equals(resourceName, process.resourceName)
        && Objects.equals(tenantId, process.tenantId);
  }

  @Override
  public String toString() {
    return "ProcessImpl{"
        + "processDefinitionKey="
        + processDefinitionKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", resourceName='"
        + resourceName
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}

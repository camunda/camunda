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

import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.client.protocol.rest.ProcessInstanceSequenceFlowResult;
import java.util.Objects;

public class ProcessInstanceSequenceFlowImpl implements ProcessInstanceSequenceFlow {

  private final String sequenceFlowId;
  private final String processInstanceKey;
  private final String rootProcessInstanceKey;
  private final String processDefinitionKey;
  private final String processDefinitionId;
  private final String elementId;
  private final String tenantId;

  public ProcessInstanceSequenceFlowImpl(final ProcessInstanceSequenceFlowResult result) {
    sequenceFlowId = result.getSequenceFlowId();
    processInstanceKey = result.getProcessInstanceKey();
    rootProcessInstanceKey = result.getRootProcessInstanceKey();
    processDefinitionKey = result.getProcessDefinitionKey();
    processDefinitionId = result.getProcessDefinitionId();
    elementId = result.getElementId();
    tenantId = result.getTenantId();
  }

  public ProcessInstanceSequenceFlowImpl(
      final String sequenceFlowId,
      final String processInstanceKey,
      final String rootProcessInstanceKey,
      final String processDefinitionKey,
      final String processDefinitionId,
      final String elementId,
      final String tenantId) {
    this.sequenceFlowId = sequenceFlowId;
    this.processInstanceKey = processInstanceKey;
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionId = processDefinitionId;
    this.elementId = elementId;
    this.tenantId = tenantId;
  }

  @Override
  public String getSequenceFlowId() {
    return sequenceFlowId;
  }

  @Override
  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        sequenceFlowId,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionKey,
        processDefinitionId,
        elementId,
        tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessInstanceSequenceFlowImpl that = (ProcessInstanceSequenceFlowImpl) o;
    return Objects.equals(sequenceFlowId, that.sequenceFlowId)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(rootProcessInstanceKey, that.rootProcessInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return String.format(
        "ProcessInstanceSequenceFlowImpl{sequenceFlowId='%s', processInstanceKey='%s', rootProcessInstanceKey='%s', processDefinitionKey='%s', processDefinitionId='%s', elementId='%s', tenantId='%s'}",
        sequenceFlowId,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionKey,
        processDefinitionId,
        elementId,
        tenantId);
  }
}

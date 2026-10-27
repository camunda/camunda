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
package io.camunda.process.test.utils;

import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;

public class ProcessInstanceSequenceFlowBuilder implements ProcessInstanceSequenceFlow {

  private String sequenceFlowId;
  private String processInstanceKey;
  private String rootProcessInstanceKey;
  private String processDefinitionKey;
  private String processDefinitionId;
  private String elementId;
  private String tenantId;

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

  public ProcessInstanceSequenceFlowBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setProcessDefinitionId(
      final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setProcessDefinitionKey(
      final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setRootProcessInstanceKey(
      final String rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setProcessInstanceKey(final String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public ProcessInstanceSequenceFlowBuilder setSequenceFlowId(final String sequenceFlowId) {
    this.sequenceFlowId = sequenceFlowId;
    return this;
  }

  public ProcessInstanceSequenceFlow build() {
    return this;
  }

  public static ProcessInstanceSequenceFlowBuilder newSequenceFlow(
      final String elementId, final long processInstanceKey) {
    return new ProcessInstanceSequenceFlowBuilder()
        .setProcessInstanceKey(String.valueOf(processInstanceKey))
        .setElementId(elementId)
        .setSequenceFlowId("sequenceFlow_" + elementId);
  }
}

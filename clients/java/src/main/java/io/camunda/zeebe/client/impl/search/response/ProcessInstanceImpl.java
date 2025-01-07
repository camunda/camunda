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
package io.camunda.zeebe.client.impl.search.response;

import io.camunda.zeebe.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceItem;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProcessInstanceImpl implements ProcessInstance {

  private final Long key;
  private final String processName;
  private final Integer processVersion;
  private final String bpmnProcessId;
  private final Long parentProcessInstanceKey;
  private final Long parentFlowNodeInstanceKey;
  private final String startDate;
  private final String endDate;
  private final String state;
  private final Boolean incident;
  private final Boolean hasActiveOperation;
  private final Long processDefinitionKey;
  private final String tenantId;
  private final String rootInstanceId;
  private final List<OperationImpl> operations;
  private final List<ProcessInstanceReferenceImpl> callHierarchy;

  public ProcessInstanceImpl(final ProcessInstanceItem item) {
    this.key = item.getKey();
    this.processName = item.getProcessDefinitionName();
    this.processVersion = item.getProcessDefinitionVersion();
    this.bpmnProcessId = item.getBpmnProcessId();
    this.parentProcessInstanceKey = item.getParentKey();
    this.parentFlowNodeInstanceKey = item.getParentFlowNodeInstanceKey();
    this.startDate = item.getStartDate();
    this.endDate = item.getEndDate();
    this.state = Optional.ofNullable(item.getState()).map(Enum::toString).orElse(null);
    this.incident = item.getIncident();
    this.hasActiveOperation = item.getHasActiveOperation();
    this.processDefinitionKey = item.getProcessDefinitionKey();
    this.tenantId = item.getTenantId();
    this.rootInstanceId = item.getRootInstanceId();
    this.operations =
        (item.getOperations() == null)
            ? null
            : item.getOperations().stream().map(OperationImpl::new).collect(Collectors.toList());
    this.callHierarchy =
        (item.getCallHierarchy() == null)
            ? null
            : item.getCallHierarchy().stream()
                .map(ProcessInstanceReferenceImpl::new)
                .collect(Collectors.toList());
  }

  @Override
  public Long getKey() {
    return key;
  }

  @Override
  public String getProcessName() {
    return processName;
  }

  @Override
  public Integer getProcessVersion() {
    return processVersion;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public Long getParentProcessInstanceKey() {
    return parentProcessInstanceKey;
  }

  @Override
  public Long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  @Override
  public String getStartDate() {
    return startDate;
  }

  @Override
  public String getEndDate() {
    return endDate;
  }

  @Override
  public String getState() {
    return state;
  }

  @Override
  public Boolean getIncident() {
    return incident;
  }

  @Override
  public Boolean getHasActiveOperation() {
    return hasActiveOperation;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getRootInstanceId() {
    return rootInstanceId;
  }

  @Override
  public List<OperationImpl> getOperations() {
    return operations;
  }

  @Override
  public List<ProcessInstanceReferenceImpl> getCallHierarchy() {
    return callHierarchy;
  }
}

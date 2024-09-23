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
import java.util.Optional;

public class ProcessInstanceImpl implements ProcessInstance {

  private final Long key;
  private final String bpmnProcessId;
  private final String processName;
  private final Integer processVersion;
  private final String processVersionTag;
  private final Long processDefinitionKey;
  private final Long rootProcessInstanceKey;
  private final Long parentProcessInstanceKey;
  private final Long parentFlowNodeInstanceKey;
  private final String treePath;
  private final String startDate;
  private final String endDate;
  private final String state;
  private final Boolean incident;
  private final String tenantId;

  public ProcessInstanceImpl(final ProcessInstanceItem item) {
    this.key = item.getKey();
    this.bpmnProcessId = item.getBpmnProcessId();
    this.processName = item.getProcessName();
    this.processVersion = item.getProcessVersion();
    this.processVersionTag = item.getProcessVersionTag();
    this.processDefinitionKey = item.getProcessDefinitionKey();
    this.rootProcessInstanceKey = item.getRootProcessInstanceKey();
    this.parentProcessInstanceKey = item.getParentProcessInstanceKey();
    this.parentFlowNodeInstanceKey = item.getParentFlowNodeInstanceKey();
    this.treePath = item.getTreePath();
    this.startDate = item.getStartDate();
    this.endDate = item.getEndDate();
    this.state = Optional.ofNullable(item.getState()).map(Enum::toString).orElse(null);
    this.incident = item.getIncident();
    this.tenantId = item.getTenantId();
  }

  @Override
  public Long getKey() {
    return key;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
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
  public String getProcessVersionTag() {
    return processVersionTag;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
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
  public String getTreePath() {
    return treePath;
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
  public String getTenantId() {
    return tenantId;
  }
}

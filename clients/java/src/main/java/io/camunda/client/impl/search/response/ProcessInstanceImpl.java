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

import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.ProcessInstanceResult;

public class ProcessInstanceImpl implements ProcessInstance {

  private final Long processInstanceKey;
  private final String processDefinitionId;
  private final String processDefinitionName;
  private final Integer processDefinitionVersion;
  private final String processDefinitionVersionTag;
  private final Long processDefinitionKey;
  private final Long parentProcessInstanceKey;
  private final Long parentFlowNodeInstanceKey;
  private final String startDate;
  private final String endDate;
  private final ProcessInstanceState state;
  private final Boolean hasIncident;
  private final String tenantId;

  public ProcessInstanceImpl(final ProcessInstanceResult item) {
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    processDefinitionId = item.getProcessDefinitionId();
    processDefinitionName = item.getProcessDefinitionName();
    processDefinitionVersion = item.getProcessDefinitionVersion();
    processDefinitionVersionTag = item.getProcessDefinitionVersionTag();
    processDefinitionKey = ParseUtil.parseLongOrNull(item.getProcessDefinitionKey());
    parentProcessInstanceKey = ParseUtil.parseLongOrNull(item.getParentProcessInstanceKey());
    parentFlowNodeInstanceKey = ParseUtil.parseLongOrNull(item.getParentFlowNodeInstanceKey());
    startDate = item.getStartDate();
    endDate = item.getEndDate();
    state = EnumUtil.convert(item.getState(), ProcessInstanceState.class);
    hasIncident = item.getHasIncident();
    tenantId = item.getTenantId();
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public String getProcessDefinitionName() {
    return processDefinitionName;
  }

  @Override
  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    return processDefinitionVersionTag;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
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
  public ProcessInstanceState getState() {
    return state;
  }

  @Override
  public Boolean getHasIncident() {
    return hasIncident;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }
}

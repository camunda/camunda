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
package io.camunda.zeebe.client.impl.search.filter;

import io.camunda.zeebe.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceStateEnum;

public class ProcessInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<ProcessInstanceFilterRequest>
    implements ProcessInstanceFilter {

  private final ProcessInstanceFilterRequest filter;

  public ProcessInstanceFilterImpl() {
    filter = new ProcessInstanceFilterRequest();
  }

  @Override
  public ProcessInstanceFilter processInstanceKey(final Long processInstanceKey) {
    filter.setProcessInstanceKey(processInstanceKey);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionId(final String processDefinitionId) {
    filter.processDefinitionId(processDefinitionId);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionName(final String processDefinitionName) {
    filter.setProcessDefinitionName(processDefinitionName);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion) {
    filter.setProcessDefinitionVersion(processDefinitionVersion);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    filter.setProcessDefinitionVersionTag(processDefinitionVersionTag);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionKey(final Long processDefinitionKey) {
    filter.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  @Override
  public ProcessInstanceFilter rootProcessInstanceKey(final Long rootProcessInstanceKey) {
    filter.setRootProcessInstanceKey(rootProcessInstanceKey);
    return this;
  }

  @Override
  public ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey) {
    filter.setParentProcessInstanceKey(parentProcessInstanceKey);
    return this;
  }

  @Override
  public ProcessInstanceFilter parentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey) {
    filter.setParentFlowNodeInstanceKey(parentFlowNodeInstanceKey);
    return this;
  }

  @Override
  public ProcessInstanceFilter treePath(final String treePath) {
    filter.setTreePath(treePath);
    return this;
  }

  @Override
  public ProcessInstanceFilter startDate(final String startDate) {
    filter.setStartDate(startDate);
    return this;
  }

  @Override
  public ProcessInstanceFilter endDate(final String endDate) {
    filter.setEndDate(endDate);
    return this;
  }

  @Override
  public ProcessInstanceFilter state(final String state) {
    filter.setState((state == null) ? null : ProcessInstanceStateEnum.fromValue(state));
    return this;
  }

  @Override
  public ProcessInstanceFilter hasIncident(final Boolean hasIncident) {
    filter.hasIncident(hasIncident);
    return this;
  }

  @Override
  public ProcessInstanceFilter tenantId(final String tenantId) {
    filter.setTenantId(tenantId);
    return this;
  }

  @Override
  protected ProcessInstanceFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

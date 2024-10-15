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
import io.camunda.zeebe.client.protocol.rest.IntegerFilter;
import io.camunda.zeebe.client.protocol.rest.LongFilter;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.zeebe.client.protocol.rest.StringFilter;

public class ProcessInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<ProcessInstanceFilterRequest>
    implements ProcessInstanceFilter {

  private final ProcessInstanceFilterRequest filter;

  public ProcessInstanceFilterImpl() {
    filter = new ProcessInstanceFilterRequest();
  }

  @Override
  public ProcessInstanceFilter processInstanceKey(final Long processInstanceKey) {
    final LongFilter filter = new LongFilter();
    filter.set$Eq(processInstanceKey);
    this.filter.setProcessInstanceKey(filter);
    return this;
  }

  @Override
  public ProcessInstanceFilter processInstanceKey(final LongFilter processInstanceKeyFilter) {
    filter.setProcessInstanceKey(processInstanceKeyFilter);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionId(final String processDefinitionId) {
    final StringFilter filter = new StringFilter();
    filter.set$Eq(processDefinitionId);
    this.filter.processDefinitionId(filter);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionId(final StringFilter processDefinitionIdFilter) {
    filter.processDefinitionId(processDefinitionIdFilter);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionName(final String processDefinitionName) {
    filter.setProcessDefinitionName(processDefinitionName);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion) {
    final IntegerFilter filter = new IntegerFilter();
    filter.set$Eq(processDefinitionVersion);
    this.filter.setProcessDefinitionVersion(filter);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersion(
      final IntegerFilter processDefinitionVersionFilter) {
    filter.setProcessDefinitionVersion(processDefinitionVersionFilter);
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
    final LongFilter filter = new LongFilter();
    filter.set$Eq(processDefinitionKey);
    this.filter.setProcessDefinitionKey(filter);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionKey(final LongFilter processDefinitionKeyFilter) {
    filter.setProcessDefinitionKey(processDefinitionKeyFilter);
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

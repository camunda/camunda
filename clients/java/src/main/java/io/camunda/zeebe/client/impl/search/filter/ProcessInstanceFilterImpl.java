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
import io.camunda.zeebe.client.impl.util.FilterUtil;
import io.camunda.zeebe.client.protocol.rest.IntegerFilterProperty;
import io.camunda.zeebe.client.protocol.rest.LongFilterProperty;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceStateFilterProperty;

public class ProcessInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<ProcessInstanceFilterRequest>
    implements ProcessInstanceFilter {

  private final ProcessInstanceFilterRequest filter;

  public ProcessInstanceFilterImpl() {
    filter = new ProcessInstanceFilterRequest();
  }

  @Override
  public ProcessInstanceFilter processInstanceKey(final Long processInstanceKey) {
    filter.setProcessInstanceKey(FilterUtil.longFilterProperty(processInstanceKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter processInstanceKey(
      final LongFilterProperty processInstanceKeyFilter) {
    filter.setProcessInstanceKey(processInstanceKeyFilter);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionId(final String processDefinitionId) {
    filter.processDefinitionId(FilterUtil.stringFilterProperty(processDefinitionId));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionName(final String processDefinitionName) {
    filter.setProcessDefinitionName(FilterUtil.stringFilterProperty(processDefinitionName));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion) {
    filter.setProcessDefinitionVersion(FilterUtil.integerFilterProperty(processDefinitionVersion));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersion(
      final IntegerFilterProperty processDefinitionVersion) {
    filter.setProcessDefinitionVersion(processDefinitionVersion);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    filter.setProcessDefinitionVersionTag(
        FilterUtil.stringFilterProperty(processDefinitionVersionTag));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionKey(final Long processDefinitionKey) {
    filter.setProcessDefinitionKey(FilterUtil.longFilterProperty(processDefinitionKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionKey(final LongFilterProperty processDefinitionKey) {
    filter.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  @Override
  public ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey) {
    filter.setParentProcessInstanceKey(FilterUtil.longFilterProperty(parentProcessInstanceKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter parentProcessInstanceKey(
      final LongFilterProperty parentProcessInstanceKey) {
    filter.setParentProcessInstanceKey(parentProcessInstanceKey);
    return this;
  }

  @Override
  public ProcessInstanceFilter parentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey) {
    filter.setParentFlowNodeInstanceKey(FilterUtil.longFilterProperty(parentFlowNodeInstanceKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter parentFlowNodeInstanceKey(
      final LongFilterProperty parentFlowNodeInstanceKey) {
    filter.setParentFlowNodeInstanceKey(parentFlowNodeInstanceKey);
    return this;
  }

  @Override
  public ProcessInstanceFilter treePath(final String treePath) {
    filter.setTreePath(FilterUtil.stringFilterProperty(treePath));
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
    if (state != null) {
      final ProcessInstanceStateFilterProperty stateFilter =
          new ProcessInstanceStateFilterProperty();
      stateFilter.$eq(ProcessInstanceStateEnum.fromValue(state));
      filter.setState(stateFilter);
    } else {
      filter.setState(null);
    }
    return this;
  }

  @Override
  public ProcessInstanceFilter hasIncident(final Boolean hasIncident) {
    filter.hasIncident(hasIncident);
    return this;
  }

  @Override
  public ProcessInstanceFilter tenantId(final String tenantId) {
    filter.setTenantId(FilterUtil.stringFilterProperty(tenantId));
    return this;
  }

  @Override
  protected ProcessInstanceFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

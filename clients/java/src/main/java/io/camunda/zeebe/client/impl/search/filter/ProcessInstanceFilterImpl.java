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
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.DateTimePropertyBuilderCallback;
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.IntegerPropertyBuilderCallback;
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.LongPropertyBuilderCallback;
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.ProcessInstanceStatePropertyBuilderCallback;
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.StringPropertyBuilderCallback;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.impl.search.filter.builder.DateTimePropertyBuilderImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.IntegerPropertyBuilderImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.LongPropertyBuilderImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.ProcessInstanceStatePropertyBuilderImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.StringPropertyBuilderImpl;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceStateEnum;
import java.time.OffsetDateTime;

public class ProcessInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<ProcessInstanceFilterRequest>
    implements ProcessInstanceFilter {

  private final ProcessInstanceFilterRequest filter;

  public ProcessInstanceFilterImpl() {
    filter = new ProcessInstanceFilterRequest();
  }

  @Override
  public ProcessInstanceFilter processInstanceKey(final Long processInstanceKey) {
    processInstanceKey(b -> b.eq(processInstanceKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter processInstanceKey(final LongPropertyBuilderCallback callback) {
    filter.setProcessInstanceKey(callback.apply(new LongPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionId(final String processDefinitionId) {
    processDefinitionId(b -> b.eq(processDefinitionId));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionId(
      final StringPropertyBuilderCallback propertyBuilder) {
    filter.processDefinitionId(propertyBuilder.apply(new StringPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionName(final String processDefinitionName) {
    processDefinitionName(b -> b.eq(processDefinitionName));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionName(final StringPropertyBuilderCallback callback) {
    filter.setProcessDefinitionName(callback.apply(new StringPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion) {
    processDefinitionVersion(b -> b.eq(processDefinitionVersion));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersion(
      final IntegerPropertyBuilderCallback callback) {
    filter.setProcessDefinitionVersion(callback.apply(new IntegerPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    processDefinitionVersionTag(b -> b.eq(processDefinitionVersionTag));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersionTag(
      final StringPropertyBuilderCallback callback) {
    filter.setProcessDefinitionVersionTag(callback.apply(new StringPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionKey(final Long processDefinitionKey) {
    processDefinitionKey(b -> b.eq(processDefinitionKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionKey(final LongPropertyBuilderCallback callback) {
    filter.setProcessDefinitionKey(callback.apply(new LongPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey) {
    parentProcessInstanceKey(b -> b.eq(parentProcessInstanceKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter parentProcessInstanceKey(
      final LongPropertyBuilderCallback callback) {
    filter.setParentProcessInstanceKey(callback.apply(new LongPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter parentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey) {
    parentFlowNodeInstanceKey(b -> b.eq(parentFlowNodeInstanceKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter parentFlowNodeInstanceKey(
      final LongPropertyBuilderCallback callback) {
    filter.setParentFlowNodeInstanceKey(callback.apply(new LongPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter treePath(final String treePath) {
    treePath(b -> b.eq(treePath));
    return this;
  }

  @Override
  public ProcessInstanceFilter treePath(final StringPropertyBuilderCallback callback) {
    filter.setTreePath(callback.apply(new StringPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter startDate(final OffsetDateTime startDate) {
    startDate(b -> b.eq(startDate));
    return this;
  }

  @Override
  public ProcessInstanceFilter startDate(final DateTimePropertyBuilderCallback callback) {
    filter.setStartDate(callback.apply(new DateTimePropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter endDate(final OffsetDateTime endDate) {
    endDate(b -> b.eq(endDate));
    return this;
  }

  @Override
  public ProcessInstanceFilter endDate(final DateTimePropertyBuilderCallback callback) {
    filter.setEndDate(callback.apply(new DateTimePropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter state(final String state) {
    return state(b -> b.eq(ProcessInstanceStateEnum.fromValue(state)));
  }

  @Override
  public ProcessInstanceFilter state(final ProcessInstanceStatePropertyBuilderCallback callback) {
    filter.setState(callback.apply(new ProcessInstanceStatePropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public ProcessInstanceFilter hasIncident(final Boolean hasIncident) {
    filter.hasIncident(hasIncident);
    return this;
  }

  @Override
  public ProcessInstanceFilter tenantId(final String tenantId) {
    tenantId(b -> b.eq(tenantId));
    return this;
  }

  @Override
  public ProcessInstanceFilter tenantId(final StringPropertyBuilderCallback callback) {
    filter.setTenantId(callback.apply(new StringPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  protected ProcessInstanceFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

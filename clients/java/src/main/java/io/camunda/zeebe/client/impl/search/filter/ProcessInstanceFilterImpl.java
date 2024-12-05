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
import io.camunda.zeebe.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.zeebe.client.api.search.filter.builder.IntegerProperty;
import io.camunda.zeebe.client.api.search.filter.builder.LongProperty;
import io.camunda.zeebe.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.zeebe.client.api.search.filter.builder.StringProperty;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.LongPropertyImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.ProcessInstanceStatePropertyImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceVariableFilterRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;

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
  public ProcessInstanceFilter processInstanceKey(final Consumer<LongProperty> fn) {
    final LongProperty property = new LongPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionId(final String processDefinitionId) {
    processDefinitionId(b -> b.eq(processDefinitionId));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.processDefinitionId(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionName(final String processDefinitionName) {
    processDefinitionName(b -> b.eq(processDefinitionName));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionName(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionName(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion) {
    processDefinitionVersion(b -> b.eq(processDefinitionVersion));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersion(final Consumer<IntegerProperty> fn) {
    final IntegerPropertyImpl property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionVersion(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    processDefinitionVersionTag(b -> b.eq(processDefinitionVersionTag));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersionTag(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionVersionTag(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionKey(final Long processDefinitionKey) {
    processDefinitionKey(b -> b.eq(processDefinitionKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionKey(final Consumer<LongProperty> fn) {
    final LongProperty property = new LongPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionKey(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey) {
    parentProcessInstanceKey(b -> b.eq(parentProcessInstanceKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter parentProcessInstanceKey(final Consumer<LongProperty> fn) {
    final LongProperty property = new LongPropertyImpl();
    fn.accept(property);
    filter.setParentProcessInstanceKey(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter parentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey) {
    parentFlowNodeInstanceKey(b -> b.eq(parentFlowNodeInstanceKey));
    return this;
  }

  @Override
  public ProcessInstanceFilter parentFlowNodeInstanceKey(final Consumer<LongProperty> fn) {
    final LongProperty property = new LongPropertyImpl();
    fn.accept(property);
    filter.setParentFlowNodeInstanceKey(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter treePath(final String treePath) {
    treePath(b -> b.eq(treePath));
    return this;
  }

  @Override
  public ProcessInstanceFilter treePath(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTreePath(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter startDate(final OffsetDateTime startDate) {
    startDate(b -> b.eq(startDate));
    return this;
  }

  @Override
  public ProcessInstanceFilter startDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setStartDate(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter endDate(final OffsetDateTime endDate) {
    endDate(b -> b.eq(endDate));
    return this;
  }

  @Override
  public ProcessInstanceFilter endDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setEndDate(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter state(final String state) {
    return state(b -> b.eq(ProcessInstanceStateEnum.fromValue(state)));
  }

  @Override
  public ProcessInstanceFilter state(final Consumer<ProcessInstanceStateProperty> fn) {
    final ProcessInstanceStateProperty property = new ProcessInstanceStatePropertyImpl();
    fn.accept(property);
    filter.setState(property.build());
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
  public ProcessInstanceFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(property.build());
    return this;
  }

  @Override
  public ProcessInstanceFilter variables(
      List<ProcessInstanceVariableFilterRequest> variableValueFilters) {
    filter.setVariables(variableValueFilters);
    return this;
  }

  @Override
  protected ProcessInstanceFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

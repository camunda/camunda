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
package io.camunda.client.impl.statistics.filter;

import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.ProcessInstanceVariableFilterRequest;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.statistics.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.client.impl.RequestMapper;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.ProcessInstanceStatePropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ProcessDefinitionStatisticsFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.BaseProcessInstanceFilter>
    implements ProcessDefinitionStatisticsFilter {

  private final io.camunda.client.protocol.rest.BaseProcessInstanceFilter filter;

  public ProcessDefinitionStatisticsFilterImpl() {
    filter = new io.camunda.client.protocol.rest.BaseProcessInstanceFilter();
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl processInstanceKey(final Long processInstanceKey) {
    processInstanceKey(b -> b.eq(processInstanceKey));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl processInstanceKey(
      final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl parentProcessInstanceKey(
      final Long parentProcessInstanceKey) {
    parentProcessInstanceKey(b -> b.eq(parentProcessInstanceKey));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl parentProcessInstanceKey(
      final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setParentProcessInstanceKey(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl parentFlowNodeInstanceKey(
      final Long parentFlowNodeInstanceKey) {
    parentFlowNodeInstanceKey(b -> b.eq(parentFlowNodeInstanceKey));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl parentFlowNodeInstanceKey(
      final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setParentFlowNodeInstanceKey(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl startDate(final OffsetDateTime startDate) {
    startDate(b -> b.eq(startDate));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl startDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setStartDate(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl endDate(final OffsetDateTime endDate) {
    endDate(b -> b.eq(endDate));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl endDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setEndDate(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl state(final ProcessInstanceState state) {
    return state(b -> b.eq(state));
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl state(
      final Consumer<ProcessInstanceStateProperty> fn) {
    final ProcessInstanceStateProperty property = new ProcessInstanceStatePropertyImpl();
    fn.accept(property);
    filter.setState(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl hasIncident(final Boolean hasIncident) {
    filter.hasIncident(hasIncident);
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl tenantId(final String tenantId) {
    tenantId(b -> b.eq(tenantId));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl variables(
      final List<ProcessInstanceVariableFilterRequest> variableValueFilters) {
    if (variableValueFilters != null) {
      variableValueFilters.forEach(v -> variableValueNullCheck(v.getValue()));
    }
    filter.setVariables(RequestMapper.toProtocolList(variableValueFilters));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl variables(
      final Map<String, Object> variableValueFilters) {
    if (variableValueFilters != null && !variableValueFilters.isEmpty()) {
      filter.setVariables(
          RequestMapper.toProtocolList(
              RequestMapper.toProcessInstanceVariableFilterRequestList(variableValueFilters)));
    }
    return this;
  }

  static void variableValueNullCheck(final Object value) {
    if (value == null) {
      throw new IllegalArgumentException("Variable value cannot be null");
    }
  }

  @Override
  protected io.camunda.client.protocol.rest.BaseProcessInstanceFilter getSearchRequestProperty() {
    return filter;
  }
}

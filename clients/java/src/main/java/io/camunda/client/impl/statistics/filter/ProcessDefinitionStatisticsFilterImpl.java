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

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.ProcessInstanceVariableFilterRequest;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.ElementInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.statistics.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.client.impl.RequestMapper;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.ElementInstanceStatePropertyImpl;
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
  public ProcessDefinitionStatisticsFilter processInstanceKey(final Long processInstanceKey) {
    processInstanceKey(b -> b.eq(processInstanceKey));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter processInstanceKey(
      final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter parentProcessInstanceKey(
      final Long parentProcessInstanceKey) {
    parentProcessInstanceKey(b -> b.eq(parentProcessInstanceKey));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter parentProcessInstanceKey(
      final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setParentProcessInstanceKey(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl parentElementInstanceKey(
      final Long parentElementInstanceKey) {
    parentElementInstanceKey(b -> b.eq(parentElementInstanceKey));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilterImpl parentElementInstanceKey(
      final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setParentElementInstanceKey(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter startDate(final OffsetDateTime startDate) {
    startDate(b -> b.eq(startDate));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter startDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setStartDate(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter endDate(final OffsetDateTime endDate) {
    endDate(b -> b.eq(endDate));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter endDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setEndDate(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter state(final ProcessInstanceState state) {
    return state(b -> b.eq(state));
  }

  @Override
  public ProcessDefinitionStatisticsFilter state(final Consumer<ProcessInstanceStateProperty> fn) {
    final ProcessInstanceStateProperty property = new ProcessInstanceStatePropertyImpl();
    fn.accept(property);
    filter.setState(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter hasIncident(final Boolean hasIncident) {
    filter.hasIncident(hasIncident);
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter tenantId(final String tenantId) {
    tenantId(b -> b.eq(tenantId));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter variables(
      final List<ProcessInstanceVariableFilterRequest> variableValueFilters) {
    if (variableValueFilters != null) {
      variableValueFilters.forEach(v -> variableValueNullCheck(v.getValue()));
    }
    filter.setVariables(RequestMapper.toProtocolList(variableValueFilters));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter variables(
      final Map<String, Object> variableValueFilters) {
    if (variableValueFilters != null && !variableValueFilters.isEmpty()) {
      filter.setVariables(
          RequestMapper.toProtocolList(
              RequestMapper.toProcessInstanceVariableFilterRequestList(variableValueFilters)));
    }
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter batchOperationId(final String batchOperationId) {
    batchOperationId(b -> b.eq(batchOperationId));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter batchOperationId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setBatchOperationId(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter errorMessage(final String errorMessage) {
    errorMessage(b -> b.eq(errorMessage));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter errorMessage(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setErrorMessage(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter hasRetriesLeft(final Boolean hasRetriesLeft) {
    filter.hasRetriesLeft(hasRetriesLeft);
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter elementId(final String elementId) {
    elementId(b -> b.eq(elementId));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter elementId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setElementId(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter elementInstanceState(
      final ElementInstanceState elementInstanceState) {
    elementInstanceState(b -> b.eq(elementInstanceState));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter elementInstanceState(
      final Consumer<ElementInstanceStateProperty> fn) {
    final ElementInstanceStateProperty property = new ElementInstanceStatePropertyImpl();
    fn.accept(property);
    filter.setElementInstanceState(RequestMapper.toProtocolObject(property.build()));
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter hasElementInstanceIncident(
      final Boolean hasElementInstanceIncident) {
    filter.hasElementInstanceIncident(hasElementInstanceIncident);
    return this;
  }

  @Override
  public ProcessDefinitionStatisticsFilter incidentErrorHashCode(
      final Integer incidentErrorHashCode) {
    filter.setIncidentErrorHashCode(incidentErrorHashCode);
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

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
package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.enums.FlowNodeInstanceFilter;
import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;

public class FlownodeInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.FlowNodeInstanceFilter>
    implements FlownodeInstanceFilter {

  private final io.camunda.client.protocol.rest.FlowNodeInstanceFilter filter;

  public FlownodeInstanceFilterImpl() {
    filter = new io.camunda.client.protocol.rest.FlowNodeInstanceFilter();
  }

  @Override
  public FlownodeInstanceFilter flowNodeInstanceKey(final long value) {
    filter.flowNodeInstanceKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public FlownodeInstanceFilter processDefinitionKey(final long value) {
    filter.setProcessDefinitionKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public FlownodeInstanceFilter processDefinitionId(final String value) {
    filter.processDefinitionId(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter processInstanceKey(final long value) {
    filter.setProcessInstanceKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public FlownodeInstanceFilter flowNodeId(final String value) {
    filter.setFlowNodeId(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter state(final FlowNodeInstanceFilter.State value) {
    filter.setState(
        EnumUtil.convert(
            value, io.camunda.client.protocol.rest.FlowNodeInstanceFilter.StateEnum.class));
    return this;
  }

  @Override
  public FlownodeInstanceFilter type(final FlowNodeInstanceFilter.Type value) {
    filter.setType(
        EnumUtil.convert(
            value, io.camunda.client.protocol.rest.FlowNodeInstanceFilter.TypeEnum.class));
    return this;
  }

  @Override
  public FlownodeInstanceFilter hasIncident(final boolean value) {
    filter.hasIncident(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter incidentKey(final long value) {
    filter.setIncidentKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public FlownodeInstanceFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.FlowNodeInstanceFilter getSearchRequestProperty() {
    return filter;
  }
}

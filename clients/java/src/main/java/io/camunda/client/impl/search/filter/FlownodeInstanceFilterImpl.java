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

import io.camunda.client.api.search.enums.FlowNodeInstanceType;
import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.filter.builder.FlowNodeInstanceStateProperty;
import io.camunda.client.api.search.response.FlowNodeInstanceState;
import io.camunda.client.impl.search.filter.builder.FlowNodeInstanceStatePropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import java.util.function.Consumer;

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
  public FlownodeInstanceFilter state(final FlowNodeInstanceState value) {
    return state(b -> b.eq(value));
  }

  @Override
  public FlownodeInstanceFilter state(final Consumer<FlowNodeInstanceStateProperty> fn) {
    final FlowNodeInstanceStateProperty property = new FlowNodeInstanceStatePropertyImpl();
    fn.accept(property);
    filter.setState(property.build());
    return this;
  }

  @Override
  public FlownodeInstanceFilter type(final FlowNodeInstanceType value) {
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

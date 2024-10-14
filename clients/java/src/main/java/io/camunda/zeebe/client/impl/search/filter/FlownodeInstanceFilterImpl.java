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

import io.camunda.zeebe.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.FlowNodeInstanceFilterRequest;

public class FlownodeInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<FlowNodeInstanceFilterRequest>
    implements FlownodeInstanceFilter {

  private final FlowNodeInstanceFilterRequest filter;

  public FlownodeInstanceFilterImpl() {
    filter = new FlowNodeInstanceFilterRequest();
  }

  @Override
  public FlownodeInstanceFilter flowNodeInstanceKey(final long value) {
    filter.flowNodeInstanceKey(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter processDefinitionKey(final long value) {
    filter.setProcessDefinitionKey(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter bpmnProcessId(final String value) {
    filter.processDefinitionId(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter processInstanceKey(final long value) {
    filter.setProcessInstanceKey(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter flowNodeId(final String value) {
    filter.setFlowNodeId(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter state(final String value) {
    filter.setState(FlowNodeInstanceFilterRequest.StateEnum.valueOf(value));
    return this;
  }

  @Override
  public FlownodeInstanceFilter type(final String value) {
    filter.setType(FlowNodeInstanceFilterRequest.TypeEnum.valueOf(value));
    return this;
  }

  @Override
  public FlownodeInstanceFilter hasIncident(final boolean value) {
    filter.hasIncident(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter incidentKey(final long value) {
    filter.setIncidentKey(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter treePath(final String value) {
    filter.setTreePath(value);
    return this;
  }

  @Override
  public FlownodeInstanceFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  protected FlowNodeInstanceFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

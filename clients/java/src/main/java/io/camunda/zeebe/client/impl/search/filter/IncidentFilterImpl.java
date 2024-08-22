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

import io.camunda.zeebe.client.api.search.filter.IncidentFilter;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.IncidentFilterRequest;

public class IncidentFilterImpl extends TypedSearchRequestPropertyProvider<IncidentFilterRequest>
    implements IncidentFilter {

  private final IncidentFilterRequest filter;

  public IncidentFilterImpl() {
    filter = new IncidentFilterRequest();
  }

  @Override
  public IncidentFilter key(final Long value) {
    filter.setKey(value);
    return this;
  }

  @Override
  public IncidentFilter processDefinitionKey(final Long value) {
    filter.setProcessDefinitionKey(value);
    return this;
  }

  @Override
  public IncidentFilter processInstanceKey(final Long value) {
    filter.setProcessInstanceKey(value);
    return this;
  }

  @Override
  public IncidentFilter type(final String type) {
    filter.setType(type);
    return this;
  }

  @Override
  public IncidentFilter flowNodeId(final String value) {
    filter.setFlowNodeId(value);
    return this;
  }

  @Override
  public IncidentFilter flowNodeInstanceId(final String value) {
    filter.setFlowNodeInstanceId(value);
    return this;
  }

  @Override
  public IncidentFilter creationTime(final String value) {
    filter.setCreationTime(value);
    return this;
  }

  @Override
  public IncidentFilter state(final String value) {
    filter.setState(value);
    return this;
  }

  @Override
  public IncidentFilter jobKey(final Long value) {
    filter.setJobKey(value);
    return this;
  }

  @Override
  public IncidentFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  public IncidentFilter hasActiveOperation(final Boolean value) {
    filter.setHasActiveOperation(value);
    return this;
  }

  @Override
  protected IncidentFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

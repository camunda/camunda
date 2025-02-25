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

import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.response.IncidentErrorType;
import io.camunda.client.api.search.response.IncidentState;
import io.camunda.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.ParseUtil;

public class IncidentFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.IncidentFilter>
    implements IncidentFilter {

  private final io.camunda.client.protocol.rest.IncidentFilter filter;

  public IncidentFilterImpl() {
    filter = new io.camunda.client.protocol.rest.IncidentFilter();
  }

  @Override
  public IncidentFilter incidentKey(final Long value) {
    filter.setIncidentKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public IncidentFilter processDefinitionKey(final Long value) {
    filter.setProcessDefinitionKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public IncidentFilter processDefinitionId(final String value) {
    filter.setProcessDefinitionId(value);
    return this;
  }

  @Override
  public IncidentFilter processInstanceKey(final Long value) {
    filter.setProcessInstanceKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public IncidentFilter errorType(final IncidentErrorType errorType) {
    filter.errorType(IncidentErrorType.toProtocolErrorType(errorType));
    return this;
  }

  @Override
  public IncidentFilter errorMessage(final String errorMessage) {
    filter.errorMessage(errorMessage);
    return this;
  }

  @Override
  public IncidentFilter flowNodeId(final String value) {
    filter.setFlowNodeId(value);
    return this;
  }

  @Override
  public IncidentFilter flowNodeInstanceKey(final Long value) {
    filter.setFlowNodeInstanceKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public IncidentFilter creationTime(final String creationTime) {
    filter.setCreationTime(creationTime);
    return this;
  }

  @Override
  public IncidentFilter state(final IncidentState value) {
    filter.setState(IncidentState.toProtocolState(value));
    return this;
  }

  @Override
  public IncidentFilter jobKey(final Long value) {
    filter.setJobKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public IncidentFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.IncidentFilter getSearchRequestProperty() {
    return filter;
  }
}

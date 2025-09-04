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

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by the new Camunda Client Java. Please see
 *     the migration guide:
 *     https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/
 */
@Deprecated
public class IncidentFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.zeebe.client.protocol.rest.IncidentFilter>
    implements IncidentFilter {

  private final io.camunda.zeebe.client.protocol.rest.IncidentFilter filter;

  public IncidentFilterImpl() {
    filter = new io.camunda.zeebe.client.protocol.rest.IncidentFilter();
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
  public IncidentFilter bpmnProcessId(final String value) {
    filter.setProcessDefinitionId(value);
    return this;
  }

  @Override
  public IncidentFilter processInstanceKey(final Long value) {
    filter.setProcessInstanceKey(value);
    return this;
  }

  @Override
  public IncidentFilter errorType(final String errorType) {
    filter.errorType(
        io.camunda.zeebe.client.protocol.rest.IncidentFilter.ErrorTypeEnum.valueOf(errorType));
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
    filter.setFlowNodeInstanceKey(value);
    return this;
  }

  @Override
  public IncidentFilter creationTime(final String creationTime) {
    filter.setCreationTime(creationTime);
    return this;
  }

  @Override
  public IncidentFilter state(final String value) {
    filter.setState(
        io.camunda.zeebe.client.protocol.rest.IncidentFilter.StateEnum.fromValue(value));
    return this;
  }

  @Override
  public IncidentFilter jobKey(final Long value) {
    filter.setJobKey(value);
    return this;
  }

  @Override
  public IncidentFilter treePath(final String treePath) {
    filter.setTreePath(treePath);
    return this;
  }

  @Override
  public IncidentFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  protected io.camunda.zeebe.client.protocol.rest.IncidentFilter getSearchRequestProperty() {
    return filter;
  }
}

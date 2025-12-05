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

import io.camunda.client.api.search.filter.AuditLogFilter;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;

public class AuditLogFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.AuditLogFilter>
    implements AuditLogFilter {

  private final io.camunda.client.protocol.rest.AuditLogFilter filter;

  public AuditLogFilterImpl() {
    filter = new io.camunda.client.protocol.rest.AuditLogFilter();
  }

  @Override
  public AuditLogFilter auditLogKey(final String value) {
    filter.setAuditLogKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogFilter processDefinitionKey(final String value) {
    filter.setProcessDefinitionKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogFilter processInstanceKey(final String value) {
    filter.setProcessInstanceKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogFilter elementInstanceKey(final String value) {
    filter.setElementInstanceKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogFilter operationType(final String value) {
    filter.setProcessDefinitionKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogFilter timestamp(final String value) {
    filter.setProcessDefinitionKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogFilter actorId(final String value) {
    filter.setProcessDefinitionKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogFilter tenantId(final String value) {
    filter.setProcessDefinitionKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogFilter deploymentKey(final String value) {
    filter.setProcessDefinitionKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogFilter formKey(final String value) {
    filter.setProcessDefinitionKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuditLogFilter resourceKey(final String value) {
    filter.setProcessDefinitionKey(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.AuditLogFilter getSearchRequestProperty() {
    return filter;
  }
}

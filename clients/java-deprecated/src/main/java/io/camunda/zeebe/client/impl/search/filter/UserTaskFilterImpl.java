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

import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.impl.search.filter.UserTaskFilterImpl}. Please see the <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>
 */
@Deprecated
public class UserTaskFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.zeebe.client.protocol.rest.io.camunda.zeebe.client.protocol.rest.UserTaskFilter>
    implements io.camunda.zeebe.client.protocol.rest.UserTaskFilter {

  private final io.camunda.zeebe.client.protocol.rest.io.camunda.zeebe.client.protocol.rest
          .UserTaskFilter
      filter;

  public UserTaskFilterImpl(
      final io.camunda.zeebe.client.protocol.rest.io.camunda.zeebe.client.protocol.rest
              .UserTaskFilter
          filter) {
    this.filter =
        new io.camunda.zeebe.client.protocol.rest.io.camunda.zeebe.client.protocol.rest
            .UserTaskFilter();
  }

  public UserTaskFilterImpl() {
    filter =
        new io.camunda.zeebe.client.protocol.rest.io.camunda.zeebe.client.protocol.rest
            .UserTaskFilter();
  }

  @Override
  public io.camunda.zeebe.client.protocol.rest.UserTaskFilter key(final Long value) {
    filter.setKey(value);
    return this;
  }

  @Override
  public io.camunda.zeebe.client.protocol.rest.UserTaskFilter state(final String state) {
    filter.setState(state);
    return this;
  }

  @Override
  public io.camunda.zeebe.client.protocol.rest.UserTaskFilter assignee(final String assignee) {
    filter.setAssignee(assignee);
    return this;
  }

  @Override
  public io.camunda.zeebe.client.protocol.rest.UserTaskFilter elementId(final String elementId) {
    filter.setElementId(elementId);
    return this;
  }

  @Override
  public io.camunda.zeebe.client.protocol.rest.UserTaskFilter candidateGroup(
      final String candidateGroup) {
    filter.setCandidateGroup(candidateGroup);
    return this;
  }

  @Override
  public io.camunda.zeebe.client.protocol.rest.UserTaskFilter candidateUser(
      final String candidateUser) {
    filter.setCandidateUser(candidateUser);
    return this;
  }

  @Override
  public io.camunda.zeebe.client.protocol.rest.UserTaskFilter processDefinitionKey(
      final Long processDefinitionKey) {
    filter.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  @Override
  public io.camunda.zeebe.client.protocol.rest.UserTaskFilter processInstanceKey(
      final Long processInstanceKey) {
    filter.setProcessInstanceKey(processInstanceKey);
    return this;
  }

  @Override
  public io.camunda.zeebe.client.protocol.rest.UserTaskFilter tentantId(final String tenantId) {
    filter.setTenantIds(tenantId);
    return this;
  }

  @Override
  public io.camunda.zeebe.client.protocol.rest.UserTaskFilter bpmnProcessId(
      final String bpmnProcessId) {
    filter.processDefinitionId(bpmnProcessId);
    return this;
  }

  @Override
  protected io.camunda.zeebe.client.protocol.rest.io.camunda.zeebe.client.protocol.rest
          .UserTaskFilter
      getSearchRequestProperty() {
    return filter;
  }
}

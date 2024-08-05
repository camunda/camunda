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
package io.camunda.zeebe.client.impl.search;

import io.camunda.zeebe.client.api.search.DecisionRequirementFilter;
import io.camunda.zeebe.client.api.search.UserTaskFilter;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementFilterRequest;
import io.camunda.zeebe.client.protocol.rest.UserTaskFilterRequest;

public class DecisionRequirementFilterImpl extends TypedSearchRequestPropertyProvider<DecisionRequirementFilterRequest>
    implements DecisionRequirementFilter {

  private final DecisionRequirementFilterRequest filter;

  public DecisionRequirementFilterImpl(final DecisionRequirementFilterRequest filter) {
    this.filter = new DecisionRequirementFilterRequest();
  }

  public DecisionRequirementFilterImpl() {
    filter = new DecisionRequirementFilterRequest();
  }

  @Override
  public DecisionRequirementFilter key(final Long key) {
    filter.setKey(key);
    return this;
  }

  @Override
  public DecisionRequirementFilter id(final String id) {
    filter.setId(id);
    return this;
  }

  @Override
  public DecisionRequirementFilter name(final String name) {
    filter.setName(name);
    return this;
  }

  @Override
  public DecisionRequirementFilter version(final Integer version) {
    filter.setVersion(version);
    return this;
  }

  @Override
  public DecisionRequirementFilter decisionRequirementsId(final String decisionRequirementsId) {
    return null;
  }

  @Override
  public DecisionRequirementFilter tenantId(final String tenantId) {
    filter.setTenantId(tenantId);
    return this;
  }


  @Override
  protected DecisionRequirementFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

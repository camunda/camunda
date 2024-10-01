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

import io.camunda.zeebe.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementsFilterRequest;

public class DecisionRequirementsFilterImpl
    extends TypedSearchRequestPropertyProvider<DecisionRequirementsFilterRequest>
    implements DecisionRequirementsFilter {

  private final DecisionRequirementsFilterRequest filter;

  public DecisionRequirementsFilterImpl(final DecisionRequirementsFilterRequest filter) {
    this.filter = new DecisionRequirementsFilterRequest();
  }

  public DecisionRequirementsFilterImpl() {
    filter = new DecisionRequirementsFilterRequest();
  }

  @Override
  public DecisionRequirementsFilter decisionRequirementsKey(final Long key) {
    filter.decisionRequirementsKey(key);
    return this;
  }

  @Override
  public DecisionRequirementsFilter name(final String name) {
    filter.name(name);
    return this;
  }

  @Override
  public DecisionRequirementsFilter version(final Integer version) {
    filter.setVersion(version);
    return this;
  }

  @Override
  public DecisionRequirementsFilter decisionRequirementsId(final String decisionRequirementsId) {
    filter.decisionRequirementsId(decisionRequirementsId);
    return this;
  }

  @Override
  public DecisionRequirementsFilter tenantId(final String tenantId) {
    filter.setTenantId(tenantId);
    return this;
  }

  @Override
  protected DecisionRequirementsFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

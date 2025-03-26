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

import io.camunda.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.ParseUtil;

public class DecisionRequirementsFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.DecisionRequirementsFilter>
    implements DecisionRequirementsFilter {

  private final io.camunda.client.protocol.rest.DecisionRequirementsFilter filter;

  public DecisionRequirementsFilterImpl(
      final io.camunda.client.protocol.rest.DecisionRequirementsFilter filter) {
    this.filter = new io.camunda.client.protocol.rest.DecisionRequirementsFilter();
  }

  public DecisionRequirementsFilterImpl() {
    filter = new io.camunda.client.protocol.rest.DecisionRequirementsFilter();
  }

  @Override
  public DecisionRequirementsFilter decisionRequirementsKey(final Long key) {
    filter.decisionRequirementsKey(ParseUtil.keyToString(key));
    return this;
  }

  @Override
  public DecisionRequirementsFilter decisionRequirementsName(final String name) {
    filter.decisionRequirementsName(name);
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
  protected io.camunda.client.protocol.rest.DecisionRequirementsFilter getSearchRequestProperty() {
    return filter;
  }
}

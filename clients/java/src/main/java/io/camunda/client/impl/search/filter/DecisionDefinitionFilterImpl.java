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

import io.camunda.client.api.search.filter.DecisionDefinitionFilter;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.ParseUtil;

public class DecisionDefinitionFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.DecisionDefinitionFilter>
    implements DecisionDefinitionFilter {

  private final io.camunda.client.protocol.rest.DecisionDefinitionFilter filter;

  public DecisionDefinitionFilterImpl() {
    filter = new io.camunda.client.protocol.rest.DecisionDefinitionFilter();
  }

  @Override
  public DecisionDefinitionFilter decisionDefinitionKey(final long value) {
    filter.setDecisionDefinitionKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public DecisionDefinitionFilter decisionDefinitionId(final String value) {
    filter.setDecisionDefinitionId(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter name(final String value) {
    filter.setName(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter version(final int value) {
    filter.setVersion(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter decisionRequirementsId(final String value) {
    filter.setDecisionRequirementsId(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter decisionRequirementsKey(final long value) {
    filter.setDecisionRequirementsKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public DecisionDefinitionFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.DecisionDefinitionFilter getSearchRequestProperty() {
    return filter;
  }
}

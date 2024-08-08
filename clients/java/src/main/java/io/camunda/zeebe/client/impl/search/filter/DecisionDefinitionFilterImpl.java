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

import io.camunda.zeebe.client.api.search.filter.DecisionDefinitionFilter;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.DecisionDefinitionFilterRequest;

public class DecisionDefinitionFilterImpl
    extends TypedSearchRequestPropertyProvider<DecisionDefinitionFilterRequest>
    implements DecisionDefinitionFilter {

  private final DecisionDefinitionFilterRequest filter;

  public DecisionDefinitionFilterImpl() {
    filter = new DecisionDefinitionFilterRequest();
  }

  @Override
  public DecisionDefinitionFilter decisionKey(final Long value) {
    filter.setDecisionKey(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter dmnDecisionId(final String value) {
    filter.setDmnDecisionId(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter dmnDecisionName(final String value) {
    filter.setDmnDecisionName(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter version(final Integer value) {
    filter.setVersion(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter dmnDecisionRequirementsId(final String value) {
    filter.setDmnDecisionRequirementsId(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter decisionRequirementsKey(final Long value) {
    filter.setDecisionRequirementsKey(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  protected DecisionDefinitionFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

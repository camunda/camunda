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

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by the new Camunda Client Java. Please see
 *     the migration guide:
 *     https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/
 */
@Deprecated
public class DecisionDefinitionFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.zeebe.client.protocol.rest.DecisionDefinitionFilter>
    implements DecisionDefinitionFilter {

  private final io.camunda.zeebe.client.protocol.rest.DecisionDefinitionFilter filter;

  public DecisionDefinitionFilterImpl() {
    filter = new io.camunda.zeebe.client.protocol.rest.DecisionDefinitionFilter();
  }

  @Override
  public DecisionDefinitionFilter decisionKey(final Long value) {
    filter.setDecisionDefinitionKey(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter dmnDecisionId(final String value) {
    filter.setDecisionDefinitionId(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter dmnDecisionName(final String value) {
    filter.setDecisionDefinitionName(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter version(final Integer value) {
    filter.setVersion(value);
    return this;
  }

  @Override
  public DecisionDefinitionFilter dmnDecisionRequirementsId(final String value) {
    filter.setDecisionRequirementsId(value);
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
  protected io.camunda.zeebe.client.protocol.rest.DecisionDefinitionFilter
      getSearchRequestProperty() {
    return filter;
  }
}

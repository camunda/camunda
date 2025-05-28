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

import io.camunda.client.api.search.filter.MappingFilter;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.MappingRuleFilterRequest;

public class MappingFilterImpl extends TypedSearchRequestPropertyProvider<MappingRuleFilterRequest>
    implements MappingFilter {

  private final MappingRuleFilterRequest filter;

  public MappingFilterImpl() {
    filter = new MappingRuleFilterRequest();
  }

  @Override
  public MappingFilter mappingRuleId(final String mappingRuleId) {
    filter.setMappingRuleId(mappingRuleId);
    return this;
  }

  @Override
  public MappingFilter claimName(final String claimName) {
    filter.setClaimName(claimName);
    return this;
  }

  @Override
  public MappingFilter claimValue(final String claimValue) {
    filter.setClaimValue(claimValue);
    return this;
  }

  @Override
  public MappingFilter name(final String name) {
    filter.setName(name);
    return this;
  }

  @Override
  protected MappingRuleFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

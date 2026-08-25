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

import io.camunda.client.api.search.filter.MappingRuleFilter;
import io.camunda.client.api.search.filter.MappingRuleFilterBase;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.MappingRuleFilterMapper;
import io.camunda.client.protocol.rest.MappingRuleFilterFields;
import java.util.List;
import java.util.function.Consumer;

public class MappingRuleFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.MappingRuleFilter>
    implements MappingRuleFilter {

  private final io.camunda.client.protocol.rest.MappingRuleFilter filter;

  public MappingRuleFilterImpl() {
    filter = new io.camunda.client.protocol.rest.MappingRuleFilter();
  }

  @Override
  public MappingRuleFilter mappingRuleId(final String mappingRuleId) {
    filter.setMappingRuleId(mappingRuleId);
    return this;
  }

  @Override
  public MappingRuleFilter claimName(final String claimName) {
    filter.setClaimName(claimName);
    return this;
  }

  @Override
  public MappingRuleFilter claimValue(final String claimValue) {
    filter.setClaimValue(claimValue);
    return this;
  }

  @Override
  public MappingRuleFilter name(final String name) {
    return name(b -> b.eq(name));
  }

  @Override
  public MappingRuleFilter name(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public MappingRuleFilterBase orFilters(final List<Consumer<MappingRuleFilterBase>> fns) {
    for (final Consumer<MappingRuleFilterBase> fn : fns) {
      final MappingRuleFilterImpl orFilter = new MappingRuleFilterImpl();
      fn.accept(orFilter);
      final MappingRuleFilterFields protocolFilterFields =
          MappingRuleFilterMapper.from(orFilter.getSearchRequestProperty());
      filter.add$OrItem(protocolFilterFields);
    }
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.MappingRuleFilter getSearchRequestProperty() {
    return filter;
  }
}

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

import io.camunda.client.api.search.filter.RoleFilter;
import io.camunda.client.api.search.filter.RoleFilterBase;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.RoleFilterMapper;
import io.camunda.client.protocol.rest.RoleFilterFields;
import java.util.List;
import java.util.function.Consumer;

public class RoleFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.RoleFilter>
    implements RoleFilter {

  private final io.camunda.client.protocol.rest.RoleFilter filter;

  public RoleFilterImpl() {
    filter = new io.camunda.client.protocol.rest.RoleFilter();
  }

  @Override
  public RoleFilter roleId(final String roleId) {
    return roleId(b -> b.eq(roleId));
  }

  @Override
  public RoleFilter roleId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setRoleId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public RoleFilter name(final String name) {
    return name(b -> b.eq(name));
  }

  @Override
  public RoleFilter name(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public RoleFilterBase orFilters(final List<Consumer<RoleFilterBase>> fns) {
    for (final Consumer<RoleFilterBase> fn : fns) {
      final RoleFilterImpl orFilter = new RoleFilterImpl();
      fn.accept(orFilter);
      final RoleFilterFields protocolFilterFields =
          RoleFilterMapper.from(orFilter.getSearchRequestProperty());
      filter.add$OrItem(protocolFilterFields);
    }
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.RoleFilter getSearchRequestProperty() {
    return filter;
  }
}

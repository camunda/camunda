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

import io.camunda.client.api.search.filter.GroupFilter;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.util.function.Consumer;

public class GroupFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.GroupFilter>
    implements GroupFilter {

  private final io.camunda.client.protocol.rest.GroupFilter filter;

  public GroupFilterImpl() {
    filter = new io.camunda.client.protocol.rest.GroupFilter();
  }

  @Override
  public GroupFilter groupId(final String groupId) {
    return groupId(b -> b.eq(groupId));
  }

  @Override
  public GroupFilter groupId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public GroupFilter name(final String name) {
    return name(b -> b.eq(name));
  }

  @Override
  public GroupFilter name(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.GroupFilter getSearchRequestProperty() {
    return filter;
  }
}

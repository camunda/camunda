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

import io.camunda.client.api.search.filter.UserTaskVariableFilter;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.util.function.Consumer;

public class UserTaskVariableFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.UserTaskVariableFilter>
    implements UserTaskVariableFilter {

  private final io.camunda.client.protocol.rest.UserTaskVariableFilter filter;

  public UserTaskVariableFilterImpl() {
    filter = new io.camunda.client.protocol.rest.UserTaskVariableFilter();
  }

  @Override
  public UserTaskVariableFilter name(final String name) {
    name(b -> b.eq(name));
    return this;
  }

  @Override
  public UserTaskVariableFilter name(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.UserTaskVariableFilter getSearchRequestProperty() {
    return filter;
  }
}

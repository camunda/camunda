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

import io.camunda.client.api.search.filter.UserFilter;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.UserFilterRequest;
import java.util.function.Consumer;

public class UserFilterImpl extends TypedSearchRequestPropertyProvider<UserFilterRequest>
    implements UserFilter {

  private final UserFilterRequest filter;

  public UserFilterImpl() {
    filter = new UserFilterRequest();
  }

  @Override
  public UserFilter username(final String username) {
    return username(b -> b.eq(username));
  }

  @Override
  public UserFilter username(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setUsername(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserFilter name(final String name) {
    return name(b -> b.eq(name));
  }

  @Override
  public UserFilter name(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserFilter email(final String email) {
    return email(b -> b.eq(email));
  }

  @Override
  public UserFilter email(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setEmail(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected UserFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

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
package io.camunda.client.impl.search.filter.builder;

import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.builder.UserTaskStateProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.UserTaskStateEnum;
import io.camunda.client.protocol.rest.UserTaskStateFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class UserTaskStatePropertyImpl
    extends TypedSearchRequestPropertyProvider<UserTaskStateFilterProperty>
    implements UserTaskStateProperty {

  private final UserTaskStateFilterProperty filterProperty = new UserTaskStateFilterProperty();

  @Override
  public UserTaskStateProperty eq(final UserTaskState value) {
    filterProperty.set$Eq(EnumUtil.convert(value, UserTaskStateEnum.class));
    return this;
  }

  @Override
  public UserTaskStateProperty neq(final UserTaskState value) {
    filterProperty.set$Neq(EnumUtil.convert(value, UserTaskStateEnum.class));
    return this;
  }

  @Override
  public UserTaskStateProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public UserTaskStateProperty in(final UserTaskState... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public UserTaskStateProperty in(final List<UserTaskState> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> (EnumUtil.convert(source, UserTaskStateEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  protected UserTaskStateFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public UserTaskStateProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}

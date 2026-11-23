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

import io.camunda.client.api.search.enums.SecretStatus;
import io.camunda.client.api.search.filter.builder.SecretStatusProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.SecretStatusEnum;
import io.camunda.client.protocol.rest.SecretStatusFilterProperty;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class SecretStatusPropertyImpl
    extends TypedSearchRequestPropertyProvider<SecretStatusFilterProperty>
    implements SecretStatusProperty {

  private final SecretStatusFilterProperty filterProperty = new SecretStatusFilterProperty();

  @Override
  public SecretStatusProperty eq(final SecretStatus value) {
    filterProperty.set$Eq(EnumUtil.convert(value, SecretStatusEnum.class));
    return this;
  }

  @Override
  public SecretStatusProperty neq(final SecretStatus value) {
    filterProperty.set$Neq(EnumUtil.convert(value, SecretStatusEnum.class));
    return this;
  }

  @Override
  public SecretStatusProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public SecretStatusProperty in(final List<SecretStatus> values) {
    filterProperty.set$In(
        values.stream()
            .map(value -> EnumUtil.convert(value, SecretStatusEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public SecretStatusProperty in(final SecretStatus... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public SecretStatusProperty notIn(final SecretStatus... values) {
    return notIn(CollectionUtil.toList(values));
  }

  @Override
  public SecretStatusProperty notIn(final Collection<SecretStatus> values) {
    filterProperty.set$NotIn(
        values.stream()
            .map(value -> EnumUtil.convert(value, SecretStatusEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public SecretStatusProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }

  @Override
  protected SecretStatusFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }
}

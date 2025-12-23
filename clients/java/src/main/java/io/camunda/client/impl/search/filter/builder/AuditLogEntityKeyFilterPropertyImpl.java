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

import io.camunda.client.api.search.filter.builder.AuditLogEntityKeyFilterProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import java.util.List;

public class AuditLogEntityKeyFilterPropertyImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.AuditLogEntityKeyFilterProperty>
    implements AuditLogEntityKeyFilterProperty {

  private final io.camunda.client.protocol.rest.AuditLogEntityKeyFilterProperty filterProperty =
      new io.camunda.client.protocol.rest.AuditLogEntityKeyFilterProperty();

  @Override
  public AuditLogEntityKeyFilterProperty eq(final String value) {
    filterProperty.set$Eq(value);
    return this;
  }

  @Override
  public AuditLogEntityKeyFilterProperty neq(final String value) {
    filterProperty.set$Neq(value);
    return this;
  }

  @Override
  public AuditLogEntityKeyFilterProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public AuditLogEntityKeyFilterProperty in(final List<String> values) {
    filterProperty.set$In(values);
    return this;
  }

  @Override
  public AuditLogEntityKeyFilterProperty in(final String... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected io.camunda.client.protocol.rest.AuditLogEntityKeyFilterProperty
      getSearchRequestProperty() {
    return filterProperty;
  }
}

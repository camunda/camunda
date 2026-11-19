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

import static io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider.provideSearchRequestProperty;

import io.camunda.client.api.search.enums.SecretStatus;
import io.camunda.client.api.search.filter.SecretListFilter;
import io.camunda.client.api.search.filter.builder.SecretStatusProperty;
import io.camunda.client.impl.search.filter.builder.SecretStatusPropertyImpl;
import io.camunda.client.protocol.rest.SecretStatusFilterProperty;
import java.util.function.Consumer;

public class SecretListFilterImpl implements SecretListFilter {

  private final io.camunda.client.protocol.rest.SecretListFilter filter =
      new io.camunda.client.protocol.rest.SecretListFilter();

  @Override
  public SecretListFilter status(final SecretStatus status) {
    if (status == null) {
      filter.setStatus(null);
      return this;
    }
    return status(s -> s.eq(status));
  }

  @Override
  public SecretListFilter status(final Consumer<SecretStatusProperty> fn) {
    final SecretStatusProperty property = new SecretStatusPropertyImpl();
    fn.accept(property);
    filter.setStatus(provideSearchRequestProperty(property));
    return this;
  }

  public boolean isEmpty() {
    final SecretStatusFilterProperty status = filter.getStatus();
    return status == null || status.equals(new SecretStatusFilterProperty());
  }

  public io.camunda.client.protocol.rest.SecretListFilter getProtocolObject() {
    return filter;
  }
}

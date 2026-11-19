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

import io.camunda.client.api.search.enums.SecretStatus;
import io.camunda.client.api.search.filter.SecretListFilter;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.SecretStatusEnum;

public class SecretListFilterImpl implements SecretListFilter {

  private final io.camunda.client.protocol.rest.SecretListFilter filter =
      new io.camunda.client.protocol.rest.SecretListFilter();

  @Override
  public SecretListFilter status(final SecretStatus status) {
    filter.setStatus(EnumUtil.convert(status, SecretStatusEnum.class));
    return this;
  }

  public boolean isEmpty() {
    return filter.getStatus() == null;
  }

  public io.camunda.client.protocol.rest.SecretListFilter getProtocolObject() {
    return filter;
  }
}

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

import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.filter.builder.MessageSubscriptionStateProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.MessageSubscriptionStateEnum;
import io.camunda.client.protocol.rest.MessageSubscriptionStateFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class MessageSubscriptionStatePropertyImpl
    extends TypedSearchRequestPropertyProvider<MessageSubscriptionStateFilterProperty>
    implements MessageSubscriptionStateProperty {

  private final MessageSubscriptionStateFilterProperty filterProperty =
      new MessageSubscriptionStateFilterProperty();

  @Override
  public MessageSubscriptionStateProperty eq(final MessageSubscriptionState value) {
    filterProperty.set$Eq(EnumUtil.convert(value, MessageSubscriptionStateEnum.class));
    return this;
  }

  @Override
  public MessageSubscriptionStateProperty neq(final MessageSubscriptionState value) {
    filterProperty.set$Neq(EnumUtil.convert(value, MessageSubscriptionStateEnum.class));
    return this;
  }

  @Override
  public MessageSubscriptionStateProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public MessageSubscriptionStateProperty in(final List<MessageSubscriptionState> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> (EnumUtil.convert(source, MessageSubscriptionStateEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public MessageSubscriptionStateProperty in(final MessageSubscriptionState... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public MessageSubscriptionStateProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }

  @Override
  protected MessageSubscriptionStateFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }
}

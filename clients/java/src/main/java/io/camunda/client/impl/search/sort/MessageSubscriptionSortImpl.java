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
package io.camunda.client.impl.search.sort;

import io.camunda.client.api.search.sort.MessageSubscriptionSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class MessageSubscriptionSortImpl extends SearchRequestSortBase<MessageSubscriptionSort>
    implements MessageSubscriptionSort {

  @Override
  public MessageSubscriptionSort messageSubscriptionKey() {
    return field("messageSubscriptionKey");
  }

  @Override
  public MessageSubscriptionSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public MessageSubscriptionSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public MessageSubscriptionSort elementId() {
    return field("elementId");
  }

  @Override
  public MessageSubscriptionSort elementInstanceKey() {
    return field("elementInstanceKey");
  }

  @Override
  public MessageSubscriptionSort messageSubscriptionType() {
    return field("messageSubscriptionType");
  }

  @Override
  public MessageSubscriptionSort lastUpdatedDate() {
    return field("lastUpdatedDate");
  }

  @Override
  public MessageSubscriptionSort messageName() {
    return field("messageName");
  }

  @Override
  public MessageSubscriptionSort correlationKey() {
    return field("correlationKey");
  }

  @Override
  public MessageSubscriptionSort tenantId() {
    return field("tenantId");
  }

  @Override
  protected MessageSubscriptionSort self() {
    return this;
  }
}

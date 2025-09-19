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

import io.camunda.client.api.search.sort.CorrelatedMessageSubscriptionSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class CorrelatedMessageSubscriptionSortImpl
    extends SearchRequestSortBase<CorrelatedMessageSubscriptionSort>
    implements CorrelatedMessageSubscriptionSort {

  @Override
  public CorrelatedMessageSubscriptionSort correlationKey() {
    return field("correlationKey");
  }

  @Override
  public CorrelatedMessageSubscriptionSort correlationTime() {
    return field("correlationTime");
  }

  @Override
  public CorrelatedMessageSubscriptionSort elementId() {
    return field("elementId");
  }

  @Override
  public CorrelatedMessageSubscriptionSort elementInstanceKey() {
    return field("elementInstanceKey");
  }

  @Override
  public CorrelatedMessageSubscriptionSort messageKey() {
    return field("messageKey");
  }

  @Override
  public CorrelatedMessageSubscriptionSort messageName() {
    return field("messageName");
  }

  @Override
  public CorrelatedMessageSubscriptionSort partitionId() {
    return field("partitionId");
  }

  @Override
  public CorrelatedMessageSubscriptionSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public CorrelatedMessageSubscriptionSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public CorrelatedMessageSubscriptionSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public CorrelatedMessageSubscriptionSort subscriptionKey() {
    return field("subscriptionKey");
  }

  @Override
  public CorrelatedMessageSubscriptionSort tenantId() {
    return field("tenantId");
  }

  @Override
  protected CorrelatedMessageSubscriptionSort self() {
    return this;
  }
}

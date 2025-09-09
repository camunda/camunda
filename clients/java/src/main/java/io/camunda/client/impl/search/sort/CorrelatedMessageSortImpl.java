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

import io.camunda.client.api.search.sort.CorrelatedMessageSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class CorrelatedMessageSortImpl extends SearchRequestSortBase<CorrelatedMessageSort>
    implements CorrelatedMessageSort {

  @Override
  public CorrelatedMessageSort correlationKey() {
    return field("correlationKey");
  }

  @Override
  public CorrelatedMessageSort correlationTime() {
    return field("correlationTime");
  }

  @Override
  public CorrelatedMessageSort elementId() {
    return field("elementId");
  }

  @Override
  public CorrelatedMessageSort elementInstanceKey() {
    return field("elementInstanceKey");
  }

  @Override
  public CorrelatedMessageSort messageKey() {
    return field("messageKey");
  }

  @Override
  public CorrelatedMessageSort messageName() {
    return field("messageName");
  }

  @Override
  public CorrelatedMessageSort partitionId() {
    return field("partitionId");
  }

  @Override
  public CorrelatedMessageSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public CorrelatedMessageSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public CorrelatedMessageSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public CorrelatedMessageSort subscriptionKey() {
    return field("subscriptionKey");
  }

  @Override
  public CorrelatedMessageSort tenantId() {
    return field("tenantId");
  }

  @Override
  protected CorrelatedMessageSort self() {
    return this;
  }
}
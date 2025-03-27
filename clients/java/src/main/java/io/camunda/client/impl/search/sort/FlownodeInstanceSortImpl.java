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

import io.camunda.client.api.search.sort.FlownodeInstanceSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class FlownodeInstanceSortImpl extends SearchRequestSortBase<FlownodeInstanceSort>
    implements FlownodeInstanceSort {

  @Override
  protected FlownodeInstanceSort self() {
    return this;
  }

  @Override
  public FlownodeInstanceSort flowNodeInstanceKey() {
    return field("flowNodeInstanceKey");
  }

  @Override
  public FlownodeInstanceSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public FlownodeInstanceSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public FlownodeInstanceSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public FlownodeInstanceSort startDate() {
    return field("startDate");
  }

  @Override
  public FlownodeInstanceSort endDate() {
    return field("endDate");
  }

  @Override
  public FlownodeInstanceSort flowNodeId() {
    return field("flowNodeId");
  }

  @Override
  public FlownodeInstanceSort type() {
    return field("type");
  }

  @Override
  public FlownodeInstanceSort state() {
    return field("state");
  }

  @Override
  public FlownodeInstanceSort incidentKey() {
    return field("incidentKey");
  }

  @Override
  public FlownodeInstanceSort tenantId() {
    return field("tenantId");
  }
}

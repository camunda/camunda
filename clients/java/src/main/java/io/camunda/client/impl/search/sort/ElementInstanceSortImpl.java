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

import io.camunda.client.api.search.sort.ElementInstanceSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class ElementInstanceSortImpl extends SearchRequestSortBase<ElementInstanceSort>
    implements ElementInstanceSort {

  @Override
  protected ElementInstanceSort self() {
    return this;
  }

  @Override
  public ElementInstanceSort elementInstanceKey() {
    return field("elementInstanceKey");
  }

  @Override
  public ElementInstanceSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public ElementInstanceSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public ElementInstanceSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public ElementInstanceSort startDate() {
    return field("startDate");
  }

  @Override
  public ElementInstanceSort endDate() {
    return field("endDate");
  }

  @Override
  public ElementInstanceSort elementId() {
    return field("elementId");
  }

  @Override
  public ElementInstanceSort type() {
    return field("type");
  }

  @Override
  public ElementInstanceSort state() {
    return field("state");
  }

  @Override
  public ElementInstanceSort incidentKey() {
    return field("incidentKey");
  }

  @Override
  public ElementInstanceSort tenantId() {
    return field("tenantId");
  }
}

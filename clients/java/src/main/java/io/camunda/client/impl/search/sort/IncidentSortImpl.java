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

import io.camunda.client.api.search.sort.IncidentSort;
import io.camunda.client.impl.search.query.SearchRequestSortBase;

public class IncidentSortImpl extends SearchRequestSortBase<IncidentSort> implements IncidentSort {

  @Override
  public IncidentSort incidentKey() {
    return field("incidentKey");
  }

  @Override
  public IncidentSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public IncidentSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public IncidentSort errorType() {
    return field("errorType");
  }

  @Override
  public IncidentSort flowNodeId() {
    return field("flowNodeId");
  }

  @Override
  public IncidentSort flowNodeInstanceKey() {
    return field("flowNodeInstanceKey");
  }

  @Override
  public IncidentSort creationTime() {
    return field("creationTime");
  }

  @Override
  public IncidentSort state() {
    return field("state");
  }

  @Override
  public IncidentSort jobKey() {
    return field("jobKey");
  }

  @Override
  public IncidentSort tenantId() {
    return field("tenantId");
  }

  @Override
  protected IncidentSort self() {
    return this;
  }
}

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

import io.camunda.client.api.search.sort.ProcessInstanceSort;
import io.camunda.client.impl.search.query.SearchRequestSortBase;

public class ProcessInstanceSortImpl extends SearchRequestSortBase<ProcessInstanceSort>
    implements ProcessInstanceSort {

  @Override
  public ProcessInstanceSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public ProcessInstanceSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public ProcessInstanceSort processDefinitionName() {
    return field("processDefinitionName");
  }

  @Override
  public ProcessInstanceSort processDefinitionVersion() {
    return field("processDefinitionVersion");
  }

  @Override
  public ProcessInstanceSort processDefinitionVersionTag() {
    return field("processDefinitionVersionTag");
  }

  @Override
  public ProcessInstanceSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public ProcessInstanceSort parentProcessInstanceKey() {
    return field("parentProcessInstanceKey");
  }

  @Override
  public ProcessInstanceSort parentFlowNodeInstanceKey() {
    return field("parentFlowNodeInstanceKey");
  }

  @Override
  public ProcessInstanceSort startDate() {
    return field("startDate");
  }

  @Override
  public ProcessInstanceSort endDate() {
    return field("endDate");
  }

  @Override
  public ProcessInstanceSort state() {
    return field("state");
  }

  @Override
  public ProcessInstanceSort hasIncident() {
    return field("hasIncident");
  }

  @Override
  public ProcessInstanceSort tenantId() {
    return field("tenantId");
  }

  @Override
  protected ProcessInstanceSort self() {
    return this;
  }
}

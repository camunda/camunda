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
package io.camunda.zeebe.client.impl.search.sort;

import io.camunda.zeebe.client.api.search.sort.ProcessInstanceSort;
import io.camunda.zeebe.client.impl.search.query.SearchQuerySortBase;

public class ProcessInstanceSortImpl extends SearchQuerySortBase<ProcessInstanceSort>
    implements ProcessInstanceSort {

  @Override
  public ProcessInstanceSort processInstanceKey() {
    return field("key");
  }

  @Override
  public ProcessInstanceSort processDefinitionId() {
    return field("bpmnProcessId");
  }

  @Override
  public ProcessInstanceSort processDefinitionName() {
    return field("processName");
  }

  @Override
  public ProcessInstanceSort processDefinitionVersion() {
    return field("processVersion");
  }

  @Override
  public ProcessInstanceSort processDefinitionVersionTag() {
    return field("processVersionTag");
  }

  @Override
  public ProcessInstanceSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public ProcessInstanceSort rootProcessInstanceKey() {
    return field("rootProcessInstanceKey");
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
  public ProcessInstanceSort treePath() {
    return field("treePath");
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
  public ProcessInstanceSort incident() {
    return field("incident");
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

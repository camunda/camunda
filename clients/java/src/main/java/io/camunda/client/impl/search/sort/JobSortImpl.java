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

import io.camunda.client.api.search.sort.JobSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class JobSortImpl extends SearchRequestSortBase<JobSort> implements JobSort {

  @Override
  public JobSort deadline() {
    return field("deadline");
  }

  @Override
  public JobSort deniedReason() {
    return field("deniedReason");
  }

  @Override
  public JobSort endTime() {
    return field("endTime");
  }

  @Override
  public JobSort errorCode() {
    return field("errorCode");
  }

  @Override
  public JobSort errorMessage() {
    return field("errorMessage");
  }

  @Override
  public JobSort hasFailedWithRetriesLeft() {
    return field("hasFailedWithRetriesLeft");
  }

  @Override
  public JobSort isDenied() {
    return field("isDenied");
  }

  @Override
  public JobSort retries() {
    return field("retries");
  }

  @Override
  public JobSort jobKey() {
    return field("jobKey");
  }

  @Override
  public JobSort type() {
    return field("type");
  }

  @Override
  public JobSort worker() {
    return field("worker");
  }

  @Override
  public JobSort state() {
    return field("state");
  }

  @Override
  public JobSort kind() {
    return field("kind");
  }

  @Override
  public JobSort listenerEventType() {
    return field("listenerEventType");
  }

  @Override
  public JobSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public JobSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public JobSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public JobSort elementId() {
    return field("elementId");
  }

  @Override
  public JobSort elementInstanceKey() {
    return field("elementInstanceKey");
  }

  @Override
  public JobSort tenantId() {
    return field("tenantId");
  }

  @Override
  protected JobSort self() {
    return this;
  }
}

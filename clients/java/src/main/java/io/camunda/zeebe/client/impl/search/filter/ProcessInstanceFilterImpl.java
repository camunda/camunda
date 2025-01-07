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
package io.camunda.zeebe.client.impl.search.filter;

import io.camunda.zeebe.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceVariableFilterRequest;

public class ProcessInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<ProcessInstanceFilterRequest>
    implements ProcessInstanceFilter {

  private final ProcessInstanceFilterRequest filter;

  public ProcessInstanceFilterImpl() {
    filter = new ProcessInstanceFilterRequest();
  }

  @Override
  public ProcessInstanceFilter running(final Boolean running) {
    filter.setRunning(running);
    return this;
  }

  @Override
  public ProcessInstanceFilter active(final Boolean active) {
    filter.setActive(active);
    return this;
  }

  @Override
  public ProcessInstanceFilter incidents(final Boolean incidents) {
    filter.setIncidents(incidents);
    return this;
  }

  @Override
  public ProcessInstanceFilter finished(final Boolean finished) {
    filter.setFinished(finished);
    return this;
  }

  @Override
  public ProcessInstanceFilter completed(final Boolean completed) {
    filter.setCompleted(completed);
    return this;
  }

  @Override
  public ProcessInstanceFilter canceled(final Boolean canceled) {
    filter.setCanceled(canceled);
    return this;
  }

  @Override
  public ProcessInstanceFilter retriesLeft(final Boolean retriesLeft) {
    filter.setRetriesLeft(retriesLeft);
    return this;
  }

  @Override
  public ProcessInstanceFilter errorMessage(final String errorMessage) {
    filter.setErrorMessage(errorMessage);
    return this;
  }

  @Override
  public ProcessInstanceFilter activityId(final String activityId) {
    filter.setActivityId(activityId);
    return this;
  }

  @Override
  public ProcessInstanceFilter startDate(final String startDate) {
    filter.setStartDate(startDate);
    return this;
  }

  @Override
  public ProcessInstanceFilter endDate(final String endDate) {
    filter.setEndDate(endDate);
    return this;
  }

  @Override
  public ProcessInstanceFilter bpmnProcessId(final String bpmnProcessId) {
    filter.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  @Override
  public ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion) {
    filter.setProcessDefinitionVersion(processDefinitionVersion);
    return this;
  }

  @Override
  public ProcessInstanceFilter variable(final ProcessInstanceVariableFilterRequest variable) {
    filter.setVariable(variable);
    return this;
  }

  @Override
  public ProcessInstanceFilter batchOperationId(final String batchOperationId) {
    filter.setBatchOperationId(batchOperationId);
    return this;
  }

  @Override
  public ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey) {
    filter.setParentProcessInstanceKey(parentProcessInstanceKey);
    return this;
  }

  @Override
  public ProcessInstanceFilter tenantId(final String tenantId) {
    filter.setTenantId(tenantId);
    return this;
  }

  @Override
  protected ProcessInstanceFilterRequest getSearchRequestProperty() {
    return filter;
  }
}

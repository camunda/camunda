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
package io.camunda.zeebe.client.api.search.filter;

import io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest;
import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestFilter;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.search.filter.ProcessInstanceFilter}
 */
@Deprecated
public interface ProcessInstanceFilter extends SearchRequestFilter {

  /** Filter by running */
  ProcessInstanceFilter running(final Boolean running);

  /** Filter by active */
  ProcessInstanceFilter active(final Boolean active);

  /** Filter by incidents */
  ProcessInstanceFilter incidents(final Boolean incidents);

  /** Filter by finished */
  ProcessInstanceFilter finished(final Boolean finished);

  /** Filter by completed */
  ProcessInstanceFilter completed(final Boolean completed);

  /** Filter by canceled */
  ProcessInstanceFilter canceled(final Boolean canceled);

  /** Filter by retriesLeft */
  ProcessInstanceFilter retriesLeft(final Boolean retriesLeft);

  /** Filter by errorMessage */
  ProcessInstanceFilter errorMessage(final String errorMessage);

  /** Filter by activityId */
  ProcessInstanceFilter activityId(final String activityId);

  /** Filter by startDate */
  ProcessInstanceFilter startDate(final String startDate);

  /** Filter by endDate */
  ProcessInstanceFilter endDate(final String endDate);

  /** Filter by bpmnProcessId */
  ProcessInstanceFilter bpmnProcessId(final String bpmnProcessId);

  /** Filter by processDefinitionVersion */
  ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion);

  /** Filter by variable */
  ProcessInstanceFilter variable(final ProcessInstanceVariableFilterRequest variable);

  /** Filter by batchOperationId */
  ProcessInstanceFilter batchOperationId(final String batchOperationId);

  /** Filter by parentProcessInstanceKey */
  ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey);

  /** Filter by tenantId */
  ProcessInstanceFilter tenantId(final String tenantId);
}

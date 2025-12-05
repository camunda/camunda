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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;

public interface AuditLogFilter extends SearchRequestFilter {
  /**
   * Filter audit logs by the audit log key
   *
   * @param auditLogKey the audit log key
   * @return the updated filter
   */
  AuditLogFilter auditLogKey(final String auditLogKey);

  /**
   * Filter audit logs by the process definition key
   *
   * @param processDefinitionKey the process definition key
   * @return the updated filter
   */
  AuditLogFilter processDefinitionKey(final String processDefinitionKey);

  /**
   * Filter audit logs by the process instance key
   *
   * @param processInstanceKey the process instance key
   * @return the updated filter
   */
  AuditLogFilter processInstanceKey(final String processInstanceKey);

  /**
   * Filter audit logs by the element instance key
   *
   * @param elementInstanceKey the element instance key
   * @return the updated filter
   */
  AuditLogFilter elementInstanceKey(final String elementInstanceKey);

  /**
   * Filter audit logs by the operation type
   *
   * @param operationType the operation type
   * @return the updated filter
   */
  AuditLogFilter operationType(final String operationType);

  /**
   * Filter audit logs by the timestamp
   *
   * @param timestamp the timestamp
   * @return the updated filter
   */
  AuditLogFilter timestamp(final String timestamp);

  /**
   * Filter audit logs by the actor id
   *
   * @param actorId the actor id
   * @return the updated filter
   */
  AuditLogFilter actorId(final String actorId);

  /**
   * Filter audit logs by the tenant id
   *
   * @param tenantId the tenant id
   * @return the updated filter
   */
  AuditLogFilter tenantId(final String tenantId);

  /**
   * Filter audit logs by the deployment key
   *
   * @param deploymentKey the deployment key
   * @return the updated filter
   */
  AuditLogFilter deploymentKey(final String deploymentKey);

  /**
   * Filter audit logs by the form key
   *
   * @param formKey the form key
   * @return the updated filter
   */
  AuditLogFilter formKey(final String formKey);

  /**
   * Filter audit logs by the resource key
   *
   * @param resourceKey the resource key
   * @return the updated filter
   */
  AuditLogFilter resourceKey(final String resourceKey);
}

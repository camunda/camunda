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

import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.filter.builder.AuditLogActorTypeFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogOperationTypeFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogResultFilterProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public interface UserTaskAuditLogFilter extends SearchRequestFilter {

  /**
   * Filter user task audit logs by the operation type
   *
   * @param operationType the operation type
   * @return the updated filter
   */
  UserTaskAuditLogFilter operationType(final AuditLogOperationTypeEnum operationType);

  /**
   * Filter user task audit logs by the operation type using {@link
   * AuditLogOperationTypeFilterProperty} consumer
   *
   * @param fn the operation type filter consumer
   * @return the updated filter
   */
  UserTaskAuditLogFilter operationType(final Consumer<AuditLogOperationTypeFilterProperty> fn);

  /**
   * Filter user task audit logs by the result
   *
   * @param result the result
   * @return the updated filter
   */
  UserTaskAuditLogFilter result(final AuditLogResultEnum result);

  /**
   * Filter user task audit logs by the result using {@link AuditLogResultFilterProperty} consumer
   *
   * @param fn the result filter consumer
   * @return the updated filter
   */
  UserTaskAuditLogFilter result(final Consumer<AuditLogResultFilterProperty> fn);

  /**
   * Filter user task audit logs by the timestamp
   *
   * @param timestamp the timestamp
   * @return the updated filter
   */
  UserTaskAuditLogFilter timestamp(final OffsetDateTime timestamp);

  /**
   * Filter user task audit logs by the timestamp using {@link DateTimeProperty} consumer
   *
   * @param fn the timestamp filter consumer
   * @return the updated filter
   */
  UserTaskAuditLogFilter timestamp(final Consumer<DateTimeProperty> fn);

  /**
   * Filter user task audit logs by the actor type
   *
   * @param actorType the actor type
   * @return the updated filter
   */
  UserTaskAuditLogFilter actorType(final AuditLogActorTypeEnum actorType);

  /**
   * Filter user task audit logs by the actor type using {@link AuditLogActorTypeFilterProperty}
   * consumer
   *
   * @param fn the actor type filter consumer
   * @return the updated filter
   */
  UserTaskAuditLogFilter actorType(final Consumer<AuditLogActorTypeFilterProperty> fn);

  /**
   * Filter user task audit logs by the actor id
   *
   * @param actorId the actor id
   * @return the updated filter
   */
  UserTaskAuditLogFilter actorId(final String actorId);

  /**
   * Filter user task audit logs by the actor id using {@link StringProperty} consumer
   *
   * @param fn the actor id filter consumer
   * @return the updated filter
   */
  UserTaskAuditLogFilter actorId(final Consumer<StringProperty> fn);
}

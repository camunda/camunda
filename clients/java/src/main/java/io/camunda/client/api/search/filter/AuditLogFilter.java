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
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.filter.builder.AuditLogActorTypeFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogCategoryFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogEntityTypeFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogOperationTypeFilterProperty;
import io.camunda.client.api.search.filter.builder.AuditLogResultFilterProperty;
import io.camunda.client.api.search.filter.builder.BasicStringProperty;
import io.camunda.client.api.search.filter.builder.BatchOperationTypeProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public interface AuditLogFilter extends SearchRequestFilter {
  /**
   * Filter audit logs by the audit log key
   *
   * @param auditLogKey the audit log key
   * @return the updated filter
   */
  AuditLogFilter auditLogKey(final String auditLogKey);

  /**
   * Filter audit logs by the audit log key using {@link BasicStringProperty} consumer
   *
   * @param fn the audit log key filter consumer
   * @return the updated filter
   */
  AuditLogFilter auditLogKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the process definition ID
   *
   * @param processDefinitionId the process definition ID
   * @return the updated filter
   */
  AuditLogFilter processDefinitionId(final String processDefinitionId);

  /**
   * Filter audit logs by the process definition ID using {@link StringProperty} consumer
   *
   * @param fn the process definition ID filter consumer
   * @return the updated filter
   */
  AuditLogFilter processDefinitionId(final Consumer<StringProperty> fn);

  /**
   * Filter audit logs by the process definition key
   *
   * @param processDefinitionKey the process definition key
   * @return the updated filter
   */
  AuditLogFilter processDefinitionKey(final String processDefinitionKey);

  /**
   * Filter audit logs by the process definition key using {@link BasicStringProperty} consumer
   *
   * @param fn the process definition key filter consumer
   * @return the updated filter
   */
  AuditLogFilter processDefinitionKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the process instance key
   *
   * @param processInstanceKey the process instance key
   * @return the updated filter
   */
  AuditLogFilter processInstanceKey(final String processInstanceKey);

  /**
   * Filter audit logs by the process instance key using {@link BasicStringProperty} consumer
   *
   * @param fn the process instance key filter consumer
   * @return the updated filter
   */
  AuditLogFilter processInstanceKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the element instance key
   *
   * @param elementInstanceKey the element instance key
   * @return the updated filter
   */
  AuditLogFilter elementInstanceKey(final String elementInstanceKey);

  /**
   * Filter audit logs by the element instance key using {@link BasicStringProperty} consumer
   *
   * @param fn the element instance key filter consumer
   * @return the updated filter
   */
  AuditLogFilter elementInstanceKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the job key
   *
   * @param jobKey the job key
   * @return the updated filter
   */
  AuditLogFilter jobKey(final String jobKey);

  /**
   * Filter audit logs by the job key using {@link BasicStringProperty} consumer
   *
   * @param fn the job key filter consumer
   * @return the updated filter
   */
  AuditLogFilter jobKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the user task key
   *
   * @param userTaskKey the user task key
   * @return the updated filter
   */
  AuditLogFilter userTaskKey(final String userTaskKey);

  /**
   * Filter audit logs by the user task key using {@link BasicStringProperty} consumer
   *
   * @param fn the user task key filter consumer
   * @return the updated filter
   */
  AuditLogFilter userTaskKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the operation type
   *
   * @param operationType the operation type
   * @return the updated filter
   */
  AuditLogFilter operationType(final AuditLogOperationTypeEnum operationType);

  /**
   * Filter audit logs by the operation type using {@link AuditLogOperationTypeFilterProperty}
   * consumer
   *
   * @param fn the operation type filter consumer
   * @return the updated filter
   */
  AuditLogFilter operationType(final Consumer<AuditLogOperationTypeFilterProperty> fn);

  /**
   * Filter audit logs by the batch operation type
   *
   * @param batchOperationType the batch operation type
   * @return the updated filter
   */
  AuditLogFilter batchOperationType(final BatchOperationType batchOperationType);

  /**
   * Filter audit logs by the batch operation type using {@link BatchOperationTypeProperty} consumer
   *
   * @param fn the batch operation type filter consumer
   * @return the updated filter
   */
  AuditLogFilter batchOperationType(final Consumer<BatchOperationTypeProperty> fn);

  /**
   * Filter audit logs by the timestamp
   *
   * @param timestamp the timestamp
   * @return the updated filter
   */
  AuditLogFilter timestamp(final OffsetDateTime timestamp);

  /**
   * Filter audit logs by the timestamp using {@link DateTimeProperty} consumer
   *
   * @param fn the timestamp filter consumer
   * @return the updated filter
   */
  AuditLogFilter timestamp(final Consumer<DateTimeProperty> fn);

  /**
   * Filter audit logs by the actor id
   *
   * @param actorId the actor id
   * @return the updated filter
   */
  AuditLogFilter actorId(final String actorId);

  /**
   * Filter audit logs by the actor id using {@link StringProperty} consumer
   *
   * @param fn the actor id filter consumer
   * @return the updated filter
   */
  AuditLogFilter actorId(final Consumer<StringProperty> fn);

  /**
   * Filter audit logs by the actor type
   *
   * @param actorType the actor type
   * @return the updated filter
   */
  AuditLogFilter actorType(final AuditLogActorTypeEnum actorType);

  /**
   * Filter audit logs by the actor type using {@link AuditLogActorTypeFilterProperty} consumer
   *
   * @param fn the actor type filter consumer
   * @return the updated filter
   */
  AuditLogFilter actorType(final Consumer<AuditLogActorTypeFilterProperty> fn);

  /**
   * Filter audit logs by the entity type
   *
   * @param entityType the entity type
   * @return the updated filter
   */
  AuditLogFilter entityType(final AuditLogEntityTypeEnum entityType);

  /**
   * Filter audit logs by the entity type using {@link AuditLogEntityTypeFilterProperty} consumer
   *
   * @param fn the entity type filter consumer
   * @return the updated filter
   */
  AuditLogFilter entityType(final Consumer<AuditLogEntityTypeFilterProperty> fn);

  /**
   * Filter audit logs by the entity key
   *
   * @param entityKey the entity type
   * @return the updated filter
   */
  AuditLogFilter entityKey(final String entityKey);

  /**
   * Filter audit logs by the entity key using {@link BasicStringProperty} consumer
   *
   * @param fn the entity key filter consumer
   * @return the updated filter
   */
  AuditLogFilter entityKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the tenant id
   *
   * @param tenantId the tenant id
   * @return the updated filter
   */
  AuditLogFilter tenantId(final String tenantId);

  /**
   * Filter audit logs by the tenant id using {@link StringProperty} consumer
   *
   * @param fn the tenant id filter consumer
   * @return the updated filter
   */
  AuditLogFilter tenantId(final Consumer<StringProperty> fn);

  /**
   * Filter audit logs by the result
   *
   * @param result the result
   * @return the updated filter
   */
  AuditLogFilter result(final AuditLogResultEnum result);

  /**
   * Filter audit logs by the result using {@link AuditLogResultFilterProperty} consumer
   *
   * @param fn the result filter consumer
   * @return the updated filter
   */
  AuditLogFilter result(final Consumer<AuditLogResultFilterProperty> fn);

  /**
   * Filter audit logs by the category
   *
   * @param category the category
   * @return the updated filter
   */
  AuditLogFilter category(final AuditLogCategoryEnum category);

  /**
   * Filter audit logs by the category using {@link AuditLogCategoryFilterProperty} consumer
   *
   * @param fn the category filter consumer
   * @return the updated filter
   */
  AuditLogFilter category(final Consumer<AuditLogCategoryFilterProperty> fn);

  /**
   * Filter audit logs by the decision requirements ID
   *
   * @param decisionRequirementsId the decision requirements ID
   * @return the updated filter
   */
  AuditLogFilter decisionRequirementsId(final String decisionRequirementsId);

  /**
   * Filter audit logs by the decision requirements ID using {@link StringProperty} consumer
   *
   * @param fn the decision requirements ID filter consumer
   * @return the updated filter
   */
  AuditLogFilter decisionRequirementsId(final Consumer<StringProperty> fn);

  /**
   * Filter audit logs by the decision requirements key
   *
   * @param decisionRequirementsKey the decision requirements key
   * @return the updated filter
   */
  AuditLogFilter decisionRequirementsKey(final String decisionRequirementsKey);

  /**
   * Filter audit logs by the decision requirements key using {@link BasicStringProperty} consumer
   *
   * @param fn the decision requirements key filter consumer
   * @return the updated filter
   */
  AuditLogFilter decisionRequirementsKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the decision definition ID
   *
   * @param decisionDefinitionId the decision definition ID
   * @return the updated filter
   */
  AuditLogFilter decisionDefinitionId(final String decisionDefinitionId);

  /**
   * Filter audit logs by the decision definition ID using {@link StringProperty} consumer
   *
   * @param fn the decision definition ID filter consumer
   * @return the updated filter
   */
  AuditLogFilter decisionDefinitionId(final Consumer<StringProperty> fn);

  /**
   * Filter audit logs by the decision definition key
   *
   * @param decisionDefinitionKey the decision definition key
   * @return the updated filter
   */
  AuditLogFilter decisionDefinitionKey(final String decisionDefinitionKey);

  /**
   * Filter audit logs by the decision definition key using {@link BasicStringProperty} consumer
   *
   * @param fn the decision definition key filter consumer
   * @return the updated filter
   */
  AuditLogFilter decisionDefinitionKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the decision evaluation key
   *
   * @param decisionEvaluationKey the decision evaluation key
   * @return the updated filter
   */
  AuditLogFilter decisionEvaluationKey(final String decisionEvaluationKey);

  /**
   * Filter audit logs by the decision evaluation key using {@link BasicStringProperty} consumer
   *
   * @param fn the decision evaluation key filter consumer
   * @return the updated filter
   */
  AuditLogFilter decisionEvaluationKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the deployment key
   *
   * @param deploymentKey the deployment key
   * @return the updated filter
   */
  AuditLogFilter deploymentKey(final String deploymentKey);

  /**
   * Filter audit logs by the deployment key using {@link BasicStringProperty} consumer
   *
   * @param fn the deployment key filter consumer
   * @return the updated filter
   */
  AuditLogFilter deploymentKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the form key
   *
   * @param formKey the form key
   * @return the updated filter
   */
  AuditLogFilter formKey(final String formKey);

  /**
   * Filter audit logs by the form key using {@link BasicStringProperty} consumer
   *
   * @param fn the form key filter consumer
   * @return the updated filter
   */
  AuditLogFilter formKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the resource key
   *
   * @param resourceKey the resource key
   * @return the updated filter
   */
  AuditLogFilter resourceKey(final String resourceKey);

  /**
   * Filter audit logs by the resource key using {@link BasicStringProperty} consumer
   *
   * @param fn the resource key filter consumer
   * @return the updated filter
   */
  AuditLogFilter resourceKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the related entity key
   *
   * @param relatedEntityKey the related entity key
   * @return the updated filter
   */
  AuditLogFilter relatedEntityKey(final String relatedEntityKey);

  /**
   * Filter audit logs by the related entity key using {@link BasicStringProperty} consumer
   *
   * @param fn the related entity key filter consumer
   * @return the updated filter
   */
  AuditLogFilter relatedEntityKey(final Consumer<BasicStringProperty> fn);

  /**
   * Filter audit logs by the related entity type
   *
   * @param relatedEntityType the related entity type
   * @return the updated filter
   */
  AuditLogFilter relatedEntityType(final AuditLogEntityTypeEnum relatedEntityType);

  /**
   * Filter audit logs by the related entity type using {@link AuditLogEntityTypeFilterProperty}
   * consumer
   *
   * @param fn the related entity type filter consumer
   * @return the updated filter
   */
  AuditLogFilter relatedEntityType(final Consumer<AuditLogEntityTypeFilterProperty> fn);

  /**
   * Filter audit logs by the entity description
   *
   * @param entityDescription the entity description
   * @return the updated filter
   */
  AuditLogFilter entityDescription(final String entityDescription);

  /**
   * Filter audit logs by the entity description using {@link StringProperty} consumer
   *
   * @param fn the entity description filter consumer
   * @return the updated filter
   */
  AuditLogFilter entityDescription(final Consumer<StringProperty> fn);
}

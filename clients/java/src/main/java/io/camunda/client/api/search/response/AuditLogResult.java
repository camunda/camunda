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
package io.camunda.client.api.search.response;

import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.enums.BatchOperationType;

public interface AuditLogResult {
  String getAuditLogKey();

  String getEntityKey();

  AuditLogEntityTypeEnum getEntityType();

  AuditLogOperationTypeEnum getOperationType();

  String getBatchOperationKey();

  BatchOperationType getBatchOperationType();

  String getTimestamp();

  String getActorId();

  AuditLogActorTypeEnum getActorType();

  String getTenantId();

  AuditLogResultEnum getResult();

  String getAnnotation();

  AuditLogCategoryEnum getCategory();

  String getProcessDefinitionId();

  String getProcessDefinitionKey();

  String getProcessInstanceKey();

  String getElementInstanceKey();

  String getJobKey();

  String getUserTaskKey();

  String getDecisionRequirementsId();

  String getDecisionRequirementsKey();

  String getDecisionDefinitionId();

  String getDecisionDefinitionKey();

  String getDecisionEvaluationKey();

  String getDeploymentKey();

  String getFormKey();

  String getResourceKey();
}

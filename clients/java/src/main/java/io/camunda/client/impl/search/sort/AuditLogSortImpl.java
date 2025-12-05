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

import io.camunda.client.api.search.sort.AuditLogSort;
import io.camunda.client.impl.search.request.SearchRequestSortBase;

public class AuditLogSortImpl extends SearchRequestSortBase<AuditLogSort> implements AuditLogSort {

  @Override
  public AuditLogSort actorId() {
    return field("actorId");
  }

  @Override
  public AuditLogSort actorType() {
    return field("actorType");
  }

  @Override
  public AuditLogSort annotation() {
    return field("annotation");
  }

  @Override
  public AuditLogSort auditLogKey() {
    return field("auditLogKey");
  }

  @Override
  public AuditLogSort batchOperationKey() {
    return field("batchOperationKey");
  }

  @Override
  public AuditLogSort batchOperationType() {
    return field("batchOperationType");
  }

  @Override
  public AuditLogSort category() {
    return field("category");
  }

  @Override
  public AuditLogSort decisionDefinitionId() {
    return field("decisionDefinitionId");
  }

  @Override
  public AuditLogSort decisionDefinitionKey() {
    return field("decisionDefinitionKey");
  }

  @Override
  public AuditLogSort decisionEvaluationKey() {
    return field("decisionEvaluationKey");
  }

  @Override
  public AuditLogSort decisionRequirementsId() {
    return field("decisionRequirementsId");
  }

  @Override
  public AuditLogSort decisionRequirementsKey() {
    return field("decisionRequirementsKey");
  }

  @Override
  public AuditLogSort elementInstanceKey() {
    return field("elementInstanceKey");
  }

  @Override
  public AuditLogSort entityKey() {
    return field("entityKey");
  }

  @Override
  public AuditLogSort entityType() {
    return field("entityType");
  }

  @Override
  public AuditLogSort jobKey() {
    return field("jobKey");
  }

  @Override
  public AuditLogSort operationType() {
    return field("operationType");
  }

  @Override
  public AuditLogSort processDefinitionId() {
    return field("processDefinitionId");
  }

  @Override
  public AuditLogSort processDefinitionKey() {
    return field("processDefinitionKey");
  }

  @Override
  public AuditLogSort processInstanceKey() {
    return field("processInstanceKey");
  }

  @Override
  public AuditLogSort result() {
    return field("result");
  }

  @Override
  public AuditLogSort tenantId() {
    return field("tenantId");
  }

  @Override
  public AuditLogSort timestamp() {
    return field("timestamp");
  }

  @Override
  public AuditLogSort userTaskKey() {
    return field("userTaskKey");
  }

  @Override
  protected AuditLogSort self() {
    return this;
  }
}

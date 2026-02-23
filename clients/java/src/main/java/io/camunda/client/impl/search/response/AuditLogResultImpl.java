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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.client.impl.util.EnumUtil;

public class AuditLogResultImpl implements AuditLogResult {

  private String auditLogKey;
  private String entityKey;
  private AuditLogEntityTypeEnum entityType;
  private AuditLogOperationTypeEnum operationType;
  private String batchOperationKey;
  private BatchOperationType batchOperationType;
  private String timestamp;
  private String actorId;
  private AuditLogActorTypeEnum actorType;
  private String agentElementId;
  private String tenantId;
  private AuditLogResultEnum result;
  private String annotation;
  private AuditLogCategoryEnum category;
  private String processDefinitionId;
  private String processDefinitionKey;
  private String processInstanceKey;
  private String rootProcessInstanceKey;
  private String elementInstanceKey;
  private String jobKey;
  private String userTaskKey;
  private String decisionRequirementsId;
  private String decisionRequirementsKey;
  private String decisionDefinitionId;
  private String decisionDefinitionKey;
  private String decisionEvaluationKey;
  private String deploymentKey;
  private String formKey;
  private String resourceKey;
  private String relatedEntityKey;
  private AuditLogEntityTypeEnum relatedEntityType;
  private String entityDescription;

  public AuditLogResultImpl(final io.camunda.client.protocol.rest.AuditLogResult item) {
    auditLogKey = item.getAuditLogKey();
    entityKey = item.getEntityKey();
    entityType = EnumUtil.convert(item.getEntityType(), AuditLogEntityTypeEnum.class);
    operationType = EnumUtil.convert(item.getOperationType(), AuditLogOperationTypeEnum.class);
    batchOperationKey = item.getBatchOperationKey();
    batchOperationType = EnumUtil.convert(item.getBatchOperationType(), BatchOperationType.class);
    timestamp = item.getTimestamp();
    actorId = item.getActorId();
    actorType = EnumUtil.convert(item.getActorType(), AuditLogActorTypeEnum.class);
    agentElementId = item.getAgentElementId();
    tenantId = item.getTenantId();
    result = EnumUtil.convert(item.getResult(), AuditLogResultEnum.class);
    annotation = item.getAnnotation();
    category = EnumUtil.convert(item.getCategory(), AuditLogCategoryEnum.class);
    processDefinitionId = item.getProcessDefinitionId();
    processDefinitionKey = item.getProcessDefinitionKey();
    processInstanceKey = item.getProcessInstanceKey();
    rootProcessInstanceKey = item.getRootProcessInstanceKey();
    elementInstanceKey = item.getElementInstanceKey();
    jobKey = item.getJobKey();
    userTaskKey = item.getUserTaskKey();
    decisionRequirementsId = item.getDecisionRequirementsId();
    decisionRequirementsKey = item.getDecisionRequirementsKey();
    decisionDefinitionId = item.getDecisionDefinitionId();
    decisionDefinitionKey = item.getDecisionDefinitionKey();
    decisionEvaluationKey = item.getDecisionEvaluationKey();
    deploymentKey = item.getDeploymentKey();
    formKey = item.getFormKey();
    resourceKey = item.getResourceKey();
    relatedEntityKey = item.getRelatedEntityKey();
    relatedEntityType = EnumUtil.convert(item.getRelatedEntityType(), AuditLogEntityTypeEnum.class);
    entityDescription = item.getEntityDescription();
  }

  @Override
  public String getAuditLogKey() {
    return auditLogKey;
  }

  public void setAuditLogKey(final String auditLogKey) {
    this.auditLogKey = auditLogKey;
  }

  @Override
  public String getEntityKey() {
    return entityKey;
  }

  @Override
  public AuditLogEntityTypeEnum getEntityType() {
    return entityType;
  }

  public void setEntityType(final AuditLogEntityTypeEnum entityType) {
    this.entityType = entityType;
  }

  @Override
  public AuditLogOperationTypeEnum getOperationType() {
    return operationType;
  }

  public void setOperationType(final AuditLogOperationTypeEnum operationType) {
    this.operationType = operationType;
  }

  @Override
  public String getBatchOperationKey() {
    return batchOperationKey;
  }

  public void setBatchOperationKey(final String batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
  }

  @Override
  public BatchOperationType getBatchOperationType() {
    return batchOperationType;
  }

  public void setBatchOperationType(final BatchOperationType batchOperationType) {
    this.batchOperationType = batchOperationType;
  }

  @Override
  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final String timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public String getActorId() {
    return actorId;
  }

  public void setActorId(final String actorId) {
    this.actorId = actorId;
  }

  @Override
  public AuditLogActorTypeEnum getActorType() {
    return actorType;
  }

  public void setActorType(final AuditLogActorTypeEnum actorType) {
    this.actorType = actorType;
  }

  @Override
  public String getAgentElementId() {
    return agentElementId;
  }

  public void setAgentElementId(final String agentElementId) {
    this.agentElementId = agentElementId;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public AuditLogResultEnum getResult() {
    return result;
  }

  public void setResult(final AuditLogResultEnum result) {
    this.result = result;
  }

  @Override
  public String getAnnotation() {
    return annotation;
  }

  public void setAnnotation(final String annotation) {
    this.annotation = annotation;
  }

  @Override
  public AuditLogCategoryEnum getCategory() {
    return category;
  }

  public void setCategory(final AuditLogCategoryEnum category) {
    this.category = category;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  @Override
  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public String getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public void setRootProcessInstanceKey(final String rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
  }

  @Override
  public String getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(final String elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  @Override
  public String getJobKey() {
    return jobKey;
  }

  public void setJobKey(final String jobKey) {
    this.jobKey = jobKey;
  }

  @Override
  public String getUserTaskKey() {
    return userTaskKey;
  }

  public void setUserTaskKey(final String userTaskKey) {
    this.userTaskKey = userTaskKey;
  }

  @Override
  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public void setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
  }

  @Override
  public String getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public void setDecisionRequirementsKey(final String decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
  }

  @Override
  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public void setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
  }

  @Override
  public String getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public void setDecisionDefinitionKey(final String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  @Override
  public String getDecisionEvaluationKey() {
    return decisionEvaluationKey;
  }

  public void setDecisionEvaluationKey(final String decisionEvaluationKey) {
    this.decisionEvaluationKey = decisionEvaluationKey;
  }

  @Override
  public String getDeploymentKey() {
    return deploymentKey;
  }

  public void setDeploymentKey(final String deploymentKey) {
    this.deploymentKey = deploymentKey;
  }

  @Override
  public String getFormKey() {
    return formKey;
  }

  public void setFormKey(final String formKey) {
    this.formKey = formKey;
  }

  @Override
  public String getResourceKey() {
    return resourceKey;
  }

  public void setResourceKey(final String resourceKey) {
    this.resourceKey = resourceKey;
  }

  @Override
  public String getRelatedEntityKey() {
    return relatedEntityKey;
  }

  public void setRelatedEntityKey(final String relatedEntityKey) {
    this.relatedEntityKey = relatedEntityKey;
  }

  @Override
  public AuditLogEntityTypeEnum getRelatedEntityType() {
    return relatedEntityType;
  }

  public void setRelatedEntityType(final AuditLogEntityTypeEnum relatedEntityType) {
    this.relatedEntityType = relatedEntityType;
  }

  @Override
  public String getEntityDescription() {
    return entityDescription;
  }

  public void setEntityDescription(final String entityDescription) {
    this.entityDescription = entityDescription;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public void setEntityKey(final String entityKey) {
    this.entityKey = entityKey;
  }
}

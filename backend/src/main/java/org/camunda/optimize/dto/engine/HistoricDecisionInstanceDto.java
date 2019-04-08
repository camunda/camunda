/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricDecisionInstanceDto implements EngineDto {
  private String id;
  private String decisionDefinitionId;
  private String decisionDefinitionKey;
  private String decisionDefinitionName;
  private OffsetDateTime evaluationTime;
  private String processDefinitionId;
  private String processDefinitionKey;
  private String processInstanceId;
  private String rootProcessInstanceId;
  private String caseDefinitionId;
  private String caseDefinitionKey;
  private String caseInstanceId;
  private String activityId;
  private String activityInstanceId;
  private String userId;
  private List<HistoricDecisionInputInstanceDto> inputs = new ArrayList<>();
  private List<HistoricDecisionOutputInstanceDto> outputs = new ArrayList<>();
  private Double collectResultValue;
  private String rootDecisionInstanceId;
  private String decisionRequirementsDefinitionId;
  private String decisionRequirementsDefinitionKey;
  private String tenantId;

  public HistoricDecisionInstanceDto() {
  }

  public String getId() {
    return this.id;
  }

  public String getDecisionDefinitionId() {
    return this.decisionDefinitionId;
  }

  public String getDecisionDefinitionKey() {
    return this.decisionDefinitionKey;
  }

  public String getDecisionDefinitionName() {
    return this.decisionDefinitionName;
  }

  public OffsetDateTime getEvaluationTime() {
    return this.evaluationTime;
  }

  public String getProcessDefinitionId() {
    return this.processDefinitionId;
  }

  public String getProcessDefinitionKey() {
    return this.processDefinitionKey;
  }

  public String getProcessInstanceId() {
    return this.processInstanceId;
  }

  public String getCaseDefinitionId() {
    return this.caseDefinitionId;
  }

  public String getCaseDefinitionKey() {
    return this.caseDefinitionKey;
  }

  public String getCaseInstanceId() {
    return this.caseInstanceId;
  }

  public String getActivityId() {
    return this.activityId;
  }

  public String getActivityInstanceId() {
    return this.activityInstanceId;
  }

  public String getUserId() {
    return this.userId;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public List<HistoricDecisionInputInstanceDto> getInputs() {
    return this.inputs;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public List<HistoricDecisionOutputInstanceDto> getOutputs() {
    return this.outputs;
  }

  public Double getCollectResultValue() {
    return this.collectResultValue;
  }

  public String getRootDecisionInstanceId() {
    return this.rootDecisionInstanceId;
  }

  public String getTenantId() {
    return this.tenantId;
  }

  public String getDecisionRequirementsDefinitionId() {
    return this.decisionRequirementsDefinitionId;
  }

  public String getDecisionRequirementsDefinitionKey() {
    return this.decisionRequirementsDefinitionKey;
  }

  public String getRootProcessInstanceId() {
    return this.rootProcessInstanceId;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
  }

  public void setDecisionDefinitionKey(final String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public void setDecisionDefinitionName(final String decisionDefinitionName) {
    this.decisionDefinitionName = decisionDefinitionName;
  }

  public void setEvaluationTime(final OffsetDateTime evaluationTime) {
    this.evaluationTime = evaluationTime;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setRootProcessInstanceId(final String rootProcessInstanceId) {
    this.rootProcessInstanceId = rootProcessInstanceId;
  }

  public void setCaseDefinitionId(final String caseDefinitionId) {
    this.caseDefinitionId = caseDefinitionId;
  }

  public void setCaseDefinitionKey(final String caseDefinitionKey) {
    this.caseDefinitionKey = caseDefinitionKey;
  }

  public void setCaseInstanceId(final String caseInstanceId) {
    this.caseInstanceId = caseInstanceId;
  }

  public void setActivityId(final String activityId) {
    this.activityId = activityId;
  }

  public void setActivityInstanceId(final String activityInstanceId) {
    this.activityInstanceId = activityInstanceId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public void setInputs(final List<HistoricDecisionInputInstanceDto> inputs) {
    this.inputs = inputs;
  }

  public void setOutputs(final List<HistoricDecisionOutputInstanceDto> outputs) {
    this.outputs = outputs;
  }

  public void setCollectResultValue(final Double collectResultValue) {
    this.collectResultValue = collectResultValue;
  }

  public void setRootDecisionInstanceId(final String rootDecisionInstanceId) {
    this.rootDecisionInstanceId = rootDecisionInstanceId;
  }

  public void setDecisionRequirementsDefinitionId(final String decisionRequirementsDefinitionId) {
    this.decisionRequirementsDefinitionId = decisionRequirementsDefinitionId;
  }

  public void setDecisionRequirementsDefinitionKey(final String decisionRequirementsDefinitionKey) {
    this.decisionRequirementsDefinitionKey = decisionRequirementsDefinitionKey;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }
}

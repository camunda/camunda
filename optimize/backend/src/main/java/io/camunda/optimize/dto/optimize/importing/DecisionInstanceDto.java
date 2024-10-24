/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DecisionInstanceDto implements OptimizeDto {

  private String decisionInstanceId;
  private String processDefinitionId;
  private String processDefinitionKey;
  private String decisionDefinitionId;
  private String decisionDefinitionKey;
  private String decisionDefinitionVersion;
  private OffsetDateTime evaluationDateTime;
  private String processInstanceId;
  private String rootProcessInstanceId;
  private String activityId;
  private Double collectResultValue;
  private String rootDecisionInstanceId;
  private List<InputInstanceDto> inputs = new ArrayList<>();
  private List<OutputInstanceDto> outputs = new ArrayList<>();
  private Set<String> matchedRules = new HashSet<>();
  private String engine;
  private String tenantId;

  public DecisionInstanceDto(
      final String decisionInstanceId,
      final String processDefinitionId,
      final String processDefinitionKey,
      final String decisionDefinitionId,
      final String decisionDefinitionKey,
      final String decisionDefinitionVersion,
      final OffsetDateTime evaluationDateTime,
      final String processInstanceId,
      final String rootProcessInstanceId,
      final String activityId,
      final Double collectResultValue,
      final String rootDecisionInstanceId,
      final List<InputInstanceDto> inputs,
      final List<OutputInstanceDto> outputs,
      final Set<String> matchedRules,
      final String engine,
      final String tenantId) {
    this.decisionInstanceId = decisionInstanceId;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.decisionDefinitionId = decisionDefinitionId;
    this.decisionDefinitionKey = decisionDefinitionKey;
    this.decisionDefinitionVersion = decisionDefinitionVersion;
    this.evaluationDateTime = evaluationDateTime;
    this.processInstanceId = processInstanceId;
    this.rootProcessInstanceId = rootProcessInstanceId;
    this.activityId = activityId;
    this.collectResultValue = collectResultValue;
    this.rootDecisionInstanceId = rootDecisionInstanceId;
    this.inputs = inputs;
    this.outputs = outputs;
    this.matchedRules = matchedRules;
    this.engine = engine;
    this.tenantId = tenantId;
  }

  public DecisionInstanceDto() {}

  public String getDecisionInstanceId() {
    return this.decisionInstanceId;
  }

  public String getProcessDefinitionId() {
    return this.processDefinitionId;
  }

  public String getProcessDefinitionKey() {
    return this.processDefinitionKey;
  }

  public String getDecisionDefinitionId() {
    return this.decisionDefinitionId;
  }

  public String getDecisionDefinitionKey() {
    return this.decisionDefinitionKey;
  }

  public String getDecisionDefinitionVersion() {
    return this.decisionDefinitionVersion;
  }

  public OffsetDateTime getEvaluationDateTime() {
    return this.evaluationDateTime;
  }

  public String getProcessInstanceId() {
    return this.processInstanceId;
  }

  public String getRootProcessInstanceId() {
    return this.rootProcessInstanceId;
  }

  public String getActivityId() {
    return this.activityId;
  }

  public Double getCollectResultValue() {
    return this.collectResultValue;
  }

  public String getRootDecisionInstanceId() {
    return this.rootDecisionInstanceId;
  }

  public List<InputInstanceDto> getInputs() {
    return this.inputs;
  }

  public List<OutputInstanceDto> getOutputs() {
    return this.outputs;
  }

  public Set<String> getMatchedRules() {
    return this.matchedRules;
  }

  public String getEngine() {
    return this.engine;
  }

  public String getTenantId() {
    return this.tenantId;
  }

  public void setDecisionInstanceId(final String decisionInstanceId) {
    this.decisionInstanceId = decisionInstanceId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
  }

  public void setDecisionDefinitionKey(final String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public void setDecisionDefinitionVersion(final String decisionDefinitionVersion) {
    this.decisionDefinitionVersion = decisionDefinitionVersion;
  }

  public void setEvaluationDateTime(final OffsetDateTime evaluationDateTime) {
    this.evaluationDateTime = evaluationDateTime;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setRootProcessInstanceId(final String rootProcessInstanceId) {
    this.rootProcessInstanceId = rootProcessInstanceId;
  }

  public void setActivityId(final String activityId) {
    this.activityId = activityId;
  }

  public void setCollectResultValue(final Double collectResultValue) {
    this.collectResultValue = collectResultValue;
  }

  public void setRootDecisionInstanceId(final String rootDecisionInstanceId) {
    this.rootDecisionInstanceId = rootDecisionInstanceId;
  }

  public void setInputs(final List<InputInstanceDto> inputs) {
    this.inputs = inputs;
  }

  public void setOutputs(final List<OutputInstanceDto> outputs) {
    this.outputs = outputs;
  }

  public void setMatchedRules(final Set<String> matchedRules) {
    this.matchedRules = matchedRules;
  }

  public void setEngine(final String engine) {
    this.engine = engine;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionInstanceDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  public String toString() {
    return "DecisionInstanceDto(decisionInstanceId="
        + this.getDecisionInstanceId()
        + ", processDefinitionId="
        + this.getProcessDefinitionId()
        + ", processDefinitionKey="
        + this.getProcessDefinitionKey()
        + ", decisionDefinitionId="
        + this.getDecisionDefinitionId()
        + ", decisionDefinitionKey="
        + this.getDecisionDefinitionKey()
        + ", decisionDefinitionVersion="
        + this.getDecisionDefinitionVersion()
        + ", evaluationDateTime="
        + this.getEvaluationDateTime()
        + ", processInstanceId="
        + this.getProcessInstanceId()
        + ", rootProcessInstanceId="
        + this.getRootProcessInstanceId()
        + ", activityId="
        + this.getActivityId()
        + ", collectResultValue="
        + this.getCollectResultValue()
        + ", rootDecisionInstanceId="
        + this.getRootDecisionInstanceId()
        + ", inputs="
        + this.getInputs()
        + ", outputs="
        + this.getOutputs()
        + ", matchedRules="
        + this.getMatchedRules()
        + ", engine="
        + this.getEngine()
        + ", tenantId="
        + this.getTenantId()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String decisionInstanceId = "decisionInstanceId";
    public static final String processDefinitionId = "processDefinitionId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String decisionDefinitionId = "decisionDefinitionId";
    public static final String decisionDefinitionKey = "decisionDefinitionKey";
    public static final String decisionDefinitionVersion = "decisionDefinitionVersion";
    public static final String evaluationDateTime = "evaluationDateTime";
    public static final String processInstanceId = "processInstanceId";
    public static final String rootProcessInstanceId = "rootProcessInstanceId";
    public static final String activityId = "activityId";
    public static final String collectResultValue = "collectResultValue";
    public static final String rootDecisionInstanceId = "rootDecisionInstanceId";
    public static final String inputs = "inputs";
    public static final String outputs = "outputs";
    public static final String matchedRules = "matchedRules";
    public static final String engine = "engine";
    public static final String tenantId = "tenantId";
  }
}

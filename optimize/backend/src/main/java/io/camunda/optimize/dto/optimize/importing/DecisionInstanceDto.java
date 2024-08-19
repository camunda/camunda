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
    return decisionInstanceId;
  }

  public DecisionInstanceDto setDecisionInstanceId(final String decisionInstanceId) {
    this.decisionInstanceId = decisionInstanceId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public DecisionInstanceDto setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public DecisionInstanceDto setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public DecisionInstanceDto setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public String getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public DecisionInstanceDto setDecisionDefinitionKey(final String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
    return this;
  }

  public String getDecisionDefinitionVersion() {
    return decisionDefinitionVersion;
  }

  public DecisionInstanceDto setDecisionDefinitionVersion(final String decisionDefinitionVersion) {
    this.decisionDefinitionVersion = decisionDefinitionVersion;
    return this;
  }

  public OffsetDateTime getEvaluationDateTime() {
    return evaluationDateTime;
  }

  public DecisionInstanceDto setEvaluationDateTime(final OffsetDateTime evaluationDateTime) {
    this.evaluationDateTime = evaluationDateTime;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public DecisionInstanceDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getRootProcessInstanceId() {
    return rootProcessInstanceId;
  }

  public DecisionInstanceDto setRootProcessInstanceId(final String rootProcessInstanceId) {
    this.rootProcessInstanceId = rootProcessInstanceId;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public DecisionInstanceDto setActivityId(final String activityId) {
    this.activityId = activityId;
    return this;
  }

  public Double getCollectResultValue() {
    return collectResultValue;
  }

  public DecisionInstanceDto setCollectResultValue(final Double collectResultValue) {
    this.collectResultValue = collectResultValue;
    return this;
  }

  public String getRootDecisionInstanceId() {
    return rootDecisionInstanceId;
  }

  public DecisionInstanceDto setRootDecisionInstanceId(final String rootDecisionInstanceId) {
    this.rootDecisionInstanceId = rootDecisionInstanceId;
    return this;
  }

  public List<InputInstanceDto> getInputs() {
    return inputs;
  }

  public DecisionInstanceDto setInputs(final List<InputInstanceDto> inputs) {
    this.inputs = inputs;
    return this;
  }

  public List<OutputInstanceDto> getOutputs() {
    return outputs;
  }

  public DecisionInstanceDto setOutputs(final List<OutputInstanceDto> outputs) {
    this.outputs = outputs;
    return this;
  }

  public Set<String> getMatchedRules() {
    return matchedRules;
  }

  public DecisionInstanceDto setMatchedRules(final Set<String> matchedRules) {
    this.matchedRules = matchedRules;
    return this;
  }

  public String getEngine() {
    return engine;
  }

  public DecisionInstanceDto setEngine(final String engine) {
    this.engine = engine;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstanceDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionInstanceDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $decisionInstanceId = getDecisionInstanceId();
    result = result * PRIME + ($decisionInstanceId == null ? 43 : $decisionInstanceId.hashCode());
    final Object $processDefinitionId = getProcessDefinitionId();
    result = result * PRIME + ($processDefinitionId == null ? 43 : $processDefinitionId.hashCode());
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $decisionDefinitionId = getDecisionDefinitionId();
    result =
        result * PRIME + ($decisionDefinitionId == null ? 43 : $decisionDefinitionId.hashCode());
    final Object $decisionDefinitionKey = getDecisionDefinitionKey();
    result =
        result * PRIME + ($decisionDefinitionKey == null ? 43 : $decisionDefinitionKey.hashCode());
    final Object $decisionDefinitionVersion = getDecisionDefinitionVersion();
    result =
        result * PRIME
            + ($decisionDefinitionVersion == null ? 43 : $decisionDefinitionVersion.hashCode());
    final Object $evaluationDateTime = getEvaluationDateTime();
    result = result * PRIME + ($evaluationDateTime == null ? 43 : $evaluationDateTime.hashCode());
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $rootProcessInstanceId = getRootProcessInstanceId();
    result =
        result * PRIME + ($rootProcessInstanceId == null ? 43 : $rootProcessInstanceId.hashCode());
    final Object $activityId = getActivityId();
    result = result * PRIME + ($activityId == null ? 43 : $activityId.hashCode());
    final Object $collectResultValue = getCollectResultValue();
    result = result * PRIME + ($collectResultValue == null ? 43 : $collectResultValue.hashCode());
    final Object $rootDecisionInstanceId = getRootDecisionInstanceId();
    result =
        result * PRIME
            + ($rootDecisionInstanceId == null ? 43 : $rootDecisionInstanceId.hashCode());
    final Object $inputs = getInputs();
    result = result * PRIME + ($inputs == null ? 43 : $inputs.hashCode());
    final Object $outputs = getOutputs();
    result = result * PRIME + ($outputs == null ? 43 : $outputs.hashCode());
    final Object $matchedRules = getMatchedRules();
    result = result * PRIME + ($matchedRules == null ? 43 : $matchedRules.hashCode());
    final Object $engine = getEngine();
    result = result * PRIME + ($engine == null ? 43 : $engine.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DecisionInstanceDto)) {
      return false;
    }
    final DecisionInstanceDto other = (DecisionInstanceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$decisionInstanceId = getDecisionInstanceId();
    final Object other$decisionInstanceId = other.getDecisionInstanceId();
    if (this$decisionInstanceId == null
        ? other$decisionInstanceId != null
        : !this$decisionInstanceId.equals(other$decisionInstanceId)) {
      return false;
    }
    final Object this$processDefinitionId = getProcessDefinitionId();
    final Object other$processDefinitionId = other.getProcessDefinitionId();
    if (this$processDefinitionId == null
        ? other$processDefinitionId != null
        : !this$processDefinitionId.equals(other$processDefinitionId)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$decisionDefinitionId = getDecisionDefinitionId();
    final Object other$decisionDefinitionId = other.getDecisionDefinitionId();
    if (this$decisionDefinitionId == null
        ? other$decisionDefinitionId != null
        : !this$decisionDefinitionId.equals(other$decisionDefinitionId)) {
      return false;
    }
    final Object this$decisionDefinitionKey = getDecisionDefinitionKey();
    final Object other$decisionDefinitionKey = other.getDecisionDefinitionKey();
    if (this$decisionDefinitionKey == null
        ? other$decisionDefinitionKey != null
        : !this$decisionDefinitionKey.equals(other$decisionDefinitionKey)) {
      return false;
    }
    final Object this$decisionDefinitionVersion = getDecisionDefinitionVersion();
    final Object other$decisionDefinitionVersion = other.getDecisionDefinitionVersion();
    if (this$decisionDefinitionVersion == null
        ? other$decisionDefinitionVersion != null
        : !this$decisionDefinitionVersion.equals(other$decisionDefinitionVersion)) {
      return false;
    }
    final Object this$evaluationDateTime = getEvaluationDateTime();
    final Object other$evaluationDateTime = other.getEvaluationDateTime();
    if (this$evaluationDateTime == null
        ? other$evaluationDateTime != null
        : !this$evaluationDateTime.equals(other$evaluationDateTime)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$rootProcessInstanceId = getRootProcessInstanceId();
    final Object other$rootProcessInstanceId = other.getRootProcessInstanceId();
    if (this$rootProcessInstanceId == null
        ? other$rootProcessInstanceId != null
        : !this$rootProcessInstanceId.equals(other$rootProcessInstanceId)) {
      return false;
    }
    final Object this$activityId = getActivityId();
    final Object other$activityId = other.getActivityId();
    if (this$activityId == null
        ? other$activityId != null
        : !this$activityId.equals(other$activityId)) {
      return false;
    }
    final Object this$collectResultValue = getCollectResultValue();
    final Object other$collectResultValue = other.getCollectResultValue();
    if (this$collectResultValue == null
        ? other$collectResultValue != null
        : !this$collectResultValue.equals(other$collectResultValue)) {
      return false;
    }
    final Object this$rootDecisionInstanceId = getRootDecisionInstanceId();
    final Object other$rootDecisionInstanceId = other.getRootDecisionInstanceId();
    if (this$rootDecisionInstanceId == null
        ? other$rootDecisionInstanceId != null
        : !this$rootDecisionInstanceId.equals(other$rootDecisionInstanceId)) {
      return false;
    }
    final Object this$inputs = getInputs();
    final Object other$inputs = other.getInputs();
    if (this$inputs == null ? other$inputs != null : !this$inputs.equals(other$inputs)) {
      return false;
    }
    final Object this$outputs = getOutputs();
    final Object other$outputs = other.getOutputs();
    if (this$outputs == null ? other$outputs != null : !this$outputs.equals(other$outputs)) {
      return false;
    }
    final Object this$matchedRules = getMatchedRules();
    final Object other$matchedRules = other.getMatchedRules();
    if (this$matchedRules == null
        ? other$matchedRules != null
        : !this$matchedRules.equals(other$matchedRules)) {
      return false;
    }
    final Object this$engine = getEngine();
    final Object other$engine = other.getEngine();
    if (this$engine == null ? other$engine != null : !this$engine.equals(other$engine)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DecisionInstanceDto(decisionInstanceId="
        + getDecisionInstanceId()
        + ", processDefinitionId="
        + getProcessDefinitionId()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", decisionDefinitionId="
        + getDecisionDefinitionId()
        + ", decisionDefinitionKey="
        + getDecisionDefinitionKey()
        + ", decisionDefinitionVersion="
        + getDecisionDefinitionVersion()
        + ", evaluationDateTime="
        + getEvaluationDateTime()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ", rootProcessInstanceId="
        + getRootProcessInstanceId()
        + ", activityId="
        + getActivityId()
        + ", collectResultValue="
        + getCollectResultValue()
        + ", rootDecisionInstanceId="
        + getRootDecisionInstanceId()
        + ", inputs="
        + getInputs()
        + ", outputs="
        + getOutputs()
        + ", matchedRules="
        + getMatchedRules()
        + ", engine="
        + getEngine()
        + ", tenantId="
        + getTenantId()
        + ")";
  }

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

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
      String decisionInstanceId,
      String processDefinitionId,
      String processDefinitionKey,
      String decisionDefinitionId,
      String decisionDefinitionKey,
      String decisionDefinitionVersion,
      OffsetDateTime evaluationDateTime,
      String processInstanceId,
      String rootProcessInstanceId,
      String activityId,
      Double collectResultValue,
      String rootDecisionInstanceId,
      List<InputInstanceDto> inputs,
      List<OutputInstanceDto> outputs,
      Set<String> matchedRules,
      String engine,
      String tenantId) {
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

  public void setDecisionInstanceId(String decisionInstanceId) {
    this.decisionInstanceId = decisionInstanceId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setDecisionDefinitionId(String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
  }

  public void setDecisionDefinitionKey(String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public void setDecisionDefinitionVersion(String decisionDefinitionVersion) {
    this.decisionDefinitionVersion = decisionDefinitionVersion;
  }

  public void setEvaluationDateTime(OffsetDateTime evaluationDateTime) {
    this.evaluationDateTime = evaluationDateTime;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setRootProcessInstanceId(String rootProcessInstanceId) {
    this.rootProcessInstanceId = rootProcessInstanceId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public void setCollectResultValue(Double collectResultValue) {
    this.collectResultValue = collectResultValue;
  }

  public void setRootDecisionInstanceId(String rootDecisionInstanceId) {
    this.rootDecisionInstanceId = rootDecisionInstanceId;
  }

  public void setInputs(List<InputInstanceDto> inputs) {
    this.inputs = inputs;
  }

  public void setOutputs(List<OutputInstanceDto> outputs) {
    this.outputs = outputs;
  }

  public void setMatchedRules(Set<String> matchedRules) {
    this.matchedRules = matchedRules;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

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
    final Object this$decisionInstanceId = this.getDecisionInstanceId();
    final Object other$decisionInstanceId = other.getDecisionInstanceId();
    if (this$decisionInstanceId == null
        ? other$decisionInstanceId != null
        : !this$decisionInstanceId.equals(other$decisionInstanceId)) {
      return false;
    }
    final Object this$processDefinitionId = this.getProcessDefinitionId();
    final Object other$processDefinitionId = other.getProcessDefinitionId();
    if (this$processDefinitionId == null
        ? other$processDefinitionId != null
        : !this$processDefinitionId.equals(other$processDefinitionId)) {
      return false;
    }
    final Object this$processDefinitionKey = this.getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$decisionDefinitionId = this.getDecisionDefinitionId();
    final Object other$decisionDefinitionId = other.getDecisionDefinitionId();
    if (this$decisionDefinitionId == null
        ? other$decisionDefinitionId != null
        : !this$decisionDefinitionId.equals(other$decisionDefinitionId)) {
      return false;
    }
    final Object this$decisionDefinitionKey = this.getDecisionDefinitionKey();
    final Object other$decisionDefinitionKey = other.getDecisionDefinitionKey();
    if (this$decisionDefinitionKey == null
        ? other$decisionDefinitionKey != null
        : !this$decisionDefinitionKey.equals(other$decisionDefinitionKey)) {
      return false;
    }
    final Object this$decisionDefinitionVersion = this.getDecisionDefinitionVersion();
    final Object other$decisionDefinitionVersion = other.getDecisionDefinitionVersion();
    if (this$decisionDefinitionVersion == null
        ? other$decisionDefinitionVersion != null
        : !this$decisionDefinitionVersion.equals(other$decisionDefinitionVersion)) {
      return false;
    }
    final Object this$evaluationDateTime = this.getEvaluationDateTime();
    final Object other$evaluationDateTime = other.getEvaluationDateTime();
    if (this$evaluationDateTime == null
        ? other$evaluationDateTime != null
        : !this$evaluationDateTime.equals(other$evaluationDateTime)) {
      return false;
    }
    final Object this$processInstanceId = this.getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$rootProcessInstanceId = this.getRootProcessInstanceId();
    final Object other$rootProcessInstanceId = other.getRootProcessInstanceId();
    if (this$rootProcessInstanceId == null
        ? other$rootProcessInstanceId != null
        : !this$rootProcessInstanceId.equals(other$rootProcessInstanceId)) {
      return false;
    }
    final Object this$activityId = this.getActivityId();
    final Object other$activityId = other.getActivityId();
    if (this$activityId == null
        ? other$activityId != null
        : !this$activityId.equals(other$activityId)) {
      return false;
    }
    final Object this$collectResultValue = this.getCollectResultValue();
    final Object other$collectResultValue = other.getCollectResultValue();
    if (this$collectResultValue == null
        ? other$collectResultValue != null
        : !this$collectResultValue.equals(other$collectResultValue)) {
      return false;
    }
    final Object this$rootDecisionInstanceId = this.getRootDecisionInstanceId();
    final Object other$rootDecisionInstanceId = other.getRootDecisionInstanceId();
    if (this$rootDecisionInstanceId == null
        ? other$rootDecisionInstanceId != null
        : !this$rootDecisionInstanceId.equals(other$rootDecisionInstanceId)) {
      return false;
    }
    final Object this$inputs = this.getInputs();
    final Object other$inputs = other.getInputs();
    if (this$inputs == null ? other$inputs != null : !this$inputs.equals(other$inputs)) {
      return false;
    }
    final Object this$outputs = this.getOutputs();
    final Object other$outputs = other.getOutputs();
    if (this$outputs == null ? other$outputs != null : !this$outputs.equals(other$outputs)) {
      return false;
    }
    final Object this$matchedRules = this.getMatchedRules();
    final Object other$matchedRules = other.getMatchedRules();
    if (this$matchedRules == null
        ? other$matchedRules != null
        : !this$matchedRules.equals(other$matchedRules)) {
      return false;
    }
    final Object this$engine = this.getEngine();
    final Object other$engine = other.getEngine();
    if (this$engine == null ? other$engine != null : !this$engine.equals(other$engine)) {
      return false;
    }
    final Object this$tenantId = this.getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionInstanceDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $decisionInstanceId = this.getDecisionInstanceId();
    result = result * PRIME + ($decisionInstanceId == null ? 43 : $decisionInstanceId.hashCode());
    final Object $processDefinitionId = this.getProcessDefinitionId();
    result = result * PRIME + ($processDefinitionId == null ? 43 : $processDefinitionId.hashCode());
    final Object $processDefinitionKey = this.getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $decisionDefinitionId = this.getDecisionDefinitionId();
    result =
        result * PRIME + ($decisionDefinitionId == null ? 43 : $decisionDefinitionId.hashCode());
    final Object $decisionDefinitionKey = this.getDecisionDefinitionKey();
    result =
        result * PRIME + ($decisionDefinitionKey == null ? 43 : $decisionDefinitionKey.hashCode());
    final Object $decisionDefinitionVersion = this.getDecisionDefinitionVersion();
    result =
        result * PRIME
            + ($decisionDefinitionVersion == null ? 43 : $decisionDefinitionVersion.hashCode());
    final Object $evaluationDateTime = this.getEvaluationDateTime();
    result = result * PRIME + ($evaluationDateTime == null ? 43 : $evaluationDateTime.hashCode());
    final Object $processInstanceId = this.getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $rootProcessInstanceId = this.getRootProcessInstanceId();
    result =
        result * PRIME + ($rootProcessInstanceId == null ? 43 : $rootProcessInstanceId.hashCode());
    final Object $activityId = this.getActivityId();
    result = result * PRIME + ($activityId == null ? 43 : $activityId.hashCode());
    final Object $collectResultValue = this.getCollectResultValue();
    result = result * PRIME + ($collectResultValue == null ? 43 : $collectResultValue.hashCode());
    final Object $rootDecisionInstanceId = this.getRootDecisionInstanceId();
    result =
        result * PRIME
            + ($rootDecisionInstanceId == null ? 43 : $rootDecisionInstanceId.hashCode());
    final Object $inputs = this.getInputs();
    result = result * PRIME + ($inputs == null ? 43 : $inputs.hashCode());
    final Object $outputs = this.getOutputs();
    result = result * PRIME + ($outputs == null ? 43 : $outputs.hashCode());
    final Object $matchedRules = this.getMatchedRules();
    result = result * PRIME + ($matchedRules == null ? 43 : $matchedRules.hashCode());
    final Object $engine = this.getEngine();
    result = result * PRIME + ($engine == null ? 43 : $engine.hashCode());
    final Object $tenantId = this.getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    return result;
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

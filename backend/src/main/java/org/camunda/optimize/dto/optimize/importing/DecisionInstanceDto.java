/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

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

  public String getDecisionInstanceId() {
    return decisionInstanceId;
  }

  public void setDecisionInstanceId(final String decisionInstanceId) {
    this.decisionInstanceId = decisionInstanceId;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public void setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
  }

  public String getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public void setDecisionDefinitionKey(final String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public String getDecisionDefinitionVersion() {
    return decisionDefinitionVersion;
  }

  public void setDecisionDefinitionVersion(final String decisionDefinitionVersion) {
    this.decisionDefinitionVersion = decisionDefinitionVersion;
  }

  public OffsetDateTime getEvaluationDateTime() {
    return evaluationDateTime;
  }

  public void setEvaluationDateTime(final OffsetDateTime evaluationDateTime) {
    this.evaluationDateTime = evaluationDateTime;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getRootProcessInstanceId() {
    return rootProcessInstanceId;
  }

  public void setRootProcessInstanceId(final String rootProcessInstanceId) {
    this.rootProcessInstanceId = rootProcessInstanceId;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(final String activityId) {
    this.activityId = activityId;
  }

  public Double getCollectResultValue() {
    return collectResultValue;
  }

  public void setCollectResultValue(final Double collectResultValue) {
    this.collectResultValue = collectResultValue;
  }

  public String getRootDecisionInstanceId() {
    return rootDecisionInstanceId;
  }

  public void setRootDecisionInstanceId(final String rootDecisionInstanceId) {
    this.rootDecisionInstanceId = rootDecisionInstanceId;
  }

  public List<InputInstanceDto> getInputs() {
    return inputs;
  }

  public void setInputs(final List<InputInstanceDto> inputs) {
    this.inputs = inputs;
  }

  public List<OutputInstanceDto> getOutputs() {
    return outputs;
  }

  public void setOutputs(final List<OutputInstanceDto> outputs) {
    this.outputs = outputs;
  }

  public Set<String> getMatchedRules() {
    return matchedRules;
  }

  public void setMatchedRules(final Set<String> matchedRules) {
    this.matchedRules = matchedRules;
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(final String engine) {
    this.engine = engine;
  }
}

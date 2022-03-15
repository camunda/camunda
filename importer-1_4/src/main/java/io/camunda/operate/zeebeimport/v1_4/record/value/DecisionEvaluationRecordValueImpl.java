/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_4.record.value;

import io.camunda.operate.zeebeimport.v1_4.record.RecordValueImpl;
import java.util.List;

public class DecisionEvaluationRecordValueImpl extends RecordValueImpl {

  private long decisionKey;
  private String decisionId;
  private String decisionName;
  private int decisionVersion;
  private String decisionRequirementsId;
  private long decisionRequirementsKey;
  private String decisionOutput;
  private String bpmnProcessId;
  private long processDefinitionKey;
  private long processInstanceKey;
  private String elementId;
  private long elementInstanceKey;
  private List<EvaluatedDecisionValueImpl> evaluatedDecisions;
  private String evaluationFailureMessage;
  private String failedDecisionId;

  public long getDecisionKey() {
    return decisionKey;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public int getDecisionVersion() {
    return decisionVersion;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public String getDecisionOutput() {
    return decisionOutput;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public String getElementId() {
    return elementId;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public List<EvaluatedDecisionValueImpl> getEvaluatedDecisions() {
    return evaluatedDecisions;
  }

  public String getEvaluationFailureMessage() {
    return evaluationFailureMessage;
  }

  public String getFailedDecisionId() {
    return failedDecisionId;
  }

  public DecisionEvaluationRecordValueImpl setDecisionKey(final long decisionKey) {
    this.decisionKey = decisionKey;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setDecisionVersion(final int decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setDecisionRequirementsId(
      final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setDecisionRequirementsKey(
      final long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setDecisionOutput(final String decisionOutput) {
    this.decisionOutput = decisionOutput;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setProcessDefinitionKey(
      final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setEvaluatedDecisions(
      final List<EvaluatedDecisionValueImpl> evaluatedDecisions) {
    this.evaluatedDecisions = evaluatedDecisions;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setEvaluationFailureMessage(
      final String evaluationFailureMessage) {
    this.evaluationFailureMessage = evaluationFailureMessage;
    return this;
  }

  public DecisionEvaluationRecordValueImpl setFailedDecisionId(final String failedDecisionId) {
    this.failedDecisionId = failedDecisionId;
    return this;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_4.record.value;

import io.camunda.operate.zeebeimport.v1_4.record.RecordValueImpl;
import java.util.List;

public class EvaluatedDecisionValueImpl extends RecordValueImpl {

  private String decisionId;
  private String decisionName;
  private long decisionKey;
  private long decisionVersion;
  private String decisionType;
  private String decisionOutput;
  private List<EvaluatedInputValueImpl> evaluatedInputs;
  private List<MatchedRuleValueImpl> matchedRules;

  public String getDecisionId() {
    return decisionId;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public long getDecisionKey() {
    return decisionKey;
  }

  public long getDecisionVersion() {
    return decisionVersion;
  }

  public String getDecisionType() {
    return decisionType;
  }

  public String getDecisionOutput() {
    return decisionOutput;
  }

  public List<EvaluatedInputValueImpl> getEvaluatedInputs() {
    return evaluatedInputs;
  }

  public List<MatchedRuleValueImpl> getMatchedRules() {
    return matchedRules;
  }

  public EvaluatedDecisionValueImpl setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public EvaluatedDecisionValueImpl setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public EvaluatedDecisionValueImpl setDecisionKey(final long decisionKey) {
    this.decisionKey = decisionKey;
    return this;
  }

  public EvaluatedDecisionValueImpl setDecisionVersion(final long decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  public EvaluatedDecisionValueImpl setDecisionType(final String decisionType) {
    this.decisionType = decisionType;
    return this;
  }

  public EvaluatedDecisionValueImpl setDecisionOutput(final String decisionOutput) {
    this.decisionOutput = decisionOutput;
    return this;
  }

  public EvaluatedDecisionValueImpl setEvaluatedInputs(
      final List<EvaluatedInputValueImpl> evaluatedInputs) {
    this.evaluatedInputs = evaluatedInputs;
    return this;
  }

  public EvaluatedDecisionValueImpl setMatchedRules(
      final List<MatchedRuleValueImpl> matchedRules) {
    this.matchedRules = matchedRules;
    return this;
  }
}

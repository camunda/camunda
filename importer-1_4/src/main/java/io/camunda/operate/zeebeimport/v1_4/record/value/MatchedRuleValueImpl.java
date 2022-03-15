/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_4.record.value;

import io.camunda.operate.zeebeimport.v1_4.record.RecordValueImpl;
import java.util.List;

public class MatchedRuleValueImpl  extends RecordValueImpl {

  private String ruleId;
  private int ruleIndex;
  private List<EvaluatedOutputValueImpl> evaluatedOutputs;

  public String getRuleId() {
    return ruleId;
  }

  public int getRuleIndex() {
    return ruleIndex;
  }

  public List<EvaluatedOutputValueImpl> getEvaluatedOutputs() {
    return evaluatedOutputs;
  }

  public MatchedRuleValueImpl setRuleId(final String ruleId) {
    this.ruleId = ruleId;
    return this;
  }

  public MatchedRuleValueImpl setRuleIndex(final int ruleIndex) {
    this.ruleIndex = ruleIndex;
    return this;
  }

  public MatchedRuleValueImpl setEvaluatedOutputs(
      final List<EvaluatedOutputValueImpl> evaluatedOutputs) {
    this.evaluatedOutputs = evaluatedOutputs;
    return this;
  }
}

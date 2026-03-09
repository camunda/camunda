/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.ParsedDecisionInput;
import io.camunda.zeebe.dmn.ParsedDecisionOutput;
import io.camunda.zeebe.dmn.ParsedDecisionRule;
import io.camunda.zeebe.dmn.ParsedDecisionTable;
import java.util.List;

public class ParsedDmnScalaDecisionTable implements ParsedDecisionTable {

  private final List<ParsedDecisionInput> inputs;
  private final List<ParsedDecisionOutput> outputs;
  private final List<ParsedDecisionRule> rules;

  public ParsedDmnScalaDecisionTable(
      final List<ParsedDecisionInput> inputs,
      final List<ParsedDecisionOutput> outputs,
      final List<ParsedDecisionRule> rules) {
    this.inputs = inputs;
    this.outputs = outputs;
    this.rules = rules;
  }

  @Override
  public List<ParsedDecisionInput> getInputs() {
    return inputs;
  }

  @Override
  public List<ParsedDecisionOutput> getOutputs() {
    return outputs;
  }

  @Override
  public List<ParsedDecisionRule> getRules() {
    return rules;
  }
}

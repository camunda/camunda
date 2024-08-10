/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.EvaluatedOutput;
import io.camunda.zeebe.dmn.MatchedRule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.camunda.dmn.Audit.EvaluatedRule;
import org.camunda.feel.syntaxtree.Val;

public record MatchedDmnScalaRule(
    String ruleId, int ruleIndex, List<EvaluatedOutput> evaluatedOutputs) implements MatchedRule {

  public static MatchedDmnScalaRule of(
      final EvaluatedRule evaluatedRule,
      final int ruleIndex,
      final Function<Val, DirectBuffer> converter) {

    final var evaluatedOutputs = new ArrayList<EvaluatedOutput>();
    evaluatedRule
        .outputs()
        .foreach(output -> evaluatedOutputs.add(EvaluatedDmnScalaOutput.of(output, converter)));

    final var rule = evaluatedRule.rule();
    return new MatchedDmnScalaRule(rule.id(), ruleIndex, evaluatedOutputs);
  }
}

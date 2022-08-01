/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

import java.util.List;
import org.agrona.DirectBuffer;

/**
 * An evaluated DMN decision. It contains details of the evaluation depending on the decision type.
 */
public interface EvaluatedDecision {

  /**
   * @return the id of the evaluated decision
   */
  String decisionId();

  /**
   * @return the name of the evaluated decision
   */
  String decisionName();

  /**
   * @return the type of the evaluated decision
   */
  DecisionType decisionType();

  /**
   * Returns the output of the evaluated decision encoded as MessagePack. If the decision was not
   * evaluated successfully then the output is {@link
   * io.camunda.zeebe.msgpack.spec.MsgPackCodes#NIL}.
   *
   * @return the output of the evaluated decision
   */
  DirectBuffer decisionOutput();

  /**
   * If the decision is a decision table then it returns the {@link EvaluatedInput evaluated
   * inputs}. The inputs are not available for other types of decision.
   *
   * @return the evaluated inputs, or an empty list if the decision is not a decision table
   */
  List<EvaluatedInput> evaluatedInputs();

  /**
   * If the decision is a decision table then it returns the matched rules. The {@link MatchedRule
   * matched rules} are not available for other types of decision.
   *
   * @return the matched rules, or an empty list if the decision is not a decision table
   */
  List<MatchedRule> matchedRules();
}

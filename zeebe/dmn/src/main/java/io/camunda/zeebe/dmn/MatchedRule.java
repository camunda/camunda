/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

import java.util.List;

/** A matched rule of a decision table. It contains details of the rule and its outputs. */
public interface MatchedRule {

  /**
   * @return the id of the matched rule
   */
  String ruleId();

  /**
   * Returns the index of the matched rule in the decision table, starting with 1.
   *
   * @return the index of the matched rule
   */
  int ruleIndex();

  /**
   * @return the evaluated outputs of the rule
   */
  List<EvaluatedOutput> evaluatedOutputs();
}

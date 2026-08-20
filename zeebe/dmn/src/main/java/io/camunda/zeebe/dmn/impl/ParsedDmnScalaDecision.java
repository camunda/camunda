/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.ParsedDecision;
import io.camunda.zeebe.dmn.ParsedDecisionTable;

public final class ParsedDmnScalaDecision implements ParsedDecision {

  private final String decisionId;
  private final String decisionName;
  private final ParsedDecisionTable decisionTable;

  public ParsedDmnScalaDecision(
      final String decisionId, final String decisionName, final ParsedDecisionTable decisionTable) {
    this.decisionId = decisionId;
    this.decisionName = decisionName;
    this.decisionTable = decisionTable;
  }

  @Override
  public String getId() {
    return decisionId;
  }

  @Override
  public String getName() {
    return decisionName;
  }

  @Override
  public ParsedDecisionTable getDecisionTable() {
    return decisionTable;
  }
}

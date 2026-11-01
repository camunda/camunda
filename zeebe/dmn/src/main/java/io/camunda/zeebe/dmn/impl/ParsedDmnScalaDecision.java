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
import org.jspecify.annotations.Nullable;

public final class ParsedDmnScalaDecision implements ParsedDecision {

  private final @Nullable String decisionId;
  private final String decisionName;
  private final @Nullable ParsedDecisionTable decisionTable;

  public ParsedDmnScalaDecision(
      final @Nullable String decisionId,
      final String decisionName,
      final @Nullable ParsedDecisionTable decisionTable) {
    this.decisionId = decisionId;
    this.decisionName = decisionName;
    this.decisionTable = decisionTable;
  }

  @Override
  public @Nullable String getId() {
    return decisionId;
  }

  @Override
  public String getName() {
    return decisionName;
  }

  @Override
  public @Nullable ParsedDecisionTable getDecisionTable() {
    return decisionTable;
  }
}

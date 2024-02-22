/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.ParsedDecision;

public final class ParsedDmnScalaDecision implements ParsedDecision {

  private final String decisionId;
  private final String decisionName;

  public ParsedDmnScalaDecision(final String decisionId, final String decisionName) {
    this.decisionId = decisionId;
    this.decisionName = decisionName;
  }

  @Override
  public String getName() {
    return decisionName;
  }

  @Override
  public String getId() {
    return decisionId;
  }
}

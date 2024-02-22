/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.ParsedDecision;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import java.util.Collections;
import java.util.List;

public final class ParseFailureMessage implements ParsedDecisionRequirementsGraph {

  private final String failureMessage;

  public ParseFailureMessage(final String failureMessage) {
    this.failureMessage = failureMessage;
  }

  @Override
  public String getFailureMessage() {
    return failureMessage;
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public String getId() {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public String getNamespace() {
    return null;
  }

  @Override
  public List<ParsedDecision> getDecisions() {
    return Collections.emptyList();
  }
}

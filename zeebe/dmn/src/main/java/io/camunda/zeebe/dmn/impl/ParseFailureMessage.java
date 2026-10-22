/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.ParsedDecision;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class ParseFailureMessage implements ParsedDecisionRequirementsGraph {

  private final @Nullable String failureMessage;

  public ParseFailureMessage(final @Nullable String failureMessage) {
    this.failureMessage = failureMessage;
  }

  @Override
  public @Nullable String getFailureMessage() {
    return failureMessage;
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public @Nullable String getId() {
    return null;
  }

  @Override
  public @Nullable String getName() {
    return null;
  }

  @Override
  public @Nullable String getNamespace() {
    return null;
  }

  @Override
  public List<ParsedDecision> getDecisions() {
    return Collections.emptyList();
  }
}

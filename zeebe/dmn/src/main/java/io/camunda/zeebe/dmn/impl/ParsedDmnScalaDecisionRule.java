/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.ParsedDecisionRule;
import org.jspecify.annotations.Nullable;

public class ParsedDmnScalaDecisionRule implements ParsedDecisionRule {

  private final @Nullable String id;

  public ParsedDmnScalaDecisionRule(final @Nullable String id) {
    this.id = id;
  }

  @Override
  public @Nullable String getId() {
    return id;
  }
}

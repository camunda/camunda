/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.ParsedDecisionRule;

public class ParsedDmnScalaDecisionRule implements ParsedDecisionRule {

  private final String id;

  public ParsedDmnScalaDecisionRule(final String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }
}

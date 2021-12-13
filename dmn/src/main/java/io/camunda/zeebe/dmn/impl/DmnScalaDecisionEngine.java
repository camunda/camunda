/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import java.io.InputStream;
import org.camunda.dmn.DmnEngine;

/**
 * A wrapper around the DMN-Scala decision engine.
 *
 * <p>
 * <li><a href="https://github.com/camunda-community-hub/dmn-scala">GitHub Repository</a>
 * <li><a href="https://github.com/camunda-community-hub/dmn-scala">Documentation</a>
 */
public final class DmnScalaDecisionEngine implements DecisionEngine {

  private final DmnEngine dmnEngine;

  public DmnScalaDecisionEngine() {
    dmnEngine = new DmnEngine.Builder().build();
  }

  @Override
  public ParsedDecisionRequirementsGraph parse(final InputStream dmnResource) {
    if (dmnResource == null) {
      throw new IllegalArgumentException("The input stream must not be null");
    }

    try {
      final var parseResult = dmnEngine.parse(dmnResource);

      if (parseResult.isLeft()) {
        final DmnEngine.Failure failure = parseResult.left().get();
        final var failureMessage = failure.message();

        return new ParseFailureMessage(failureMessage);

      } else {
        final var parsedDmn = parseResult.right().get();

        return ParsedDmnScalaDrg.of(parsedDmn);
      }

    } catch (Exception e) {
      final var failureMessage = e.getMessage();
      return new ParseFailureMessage(failureMessage);
    }
  }
}

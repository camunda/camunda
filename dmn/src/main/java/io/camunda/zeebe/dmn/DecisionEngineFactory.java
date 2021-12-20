/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

import io.camunda.zeebe.dmn.impl.DmnScalaDecisionEngine;

/** The entry point to create a new {@link DecisionEngine}. */
public final class DecisionEngineFactory {

  /** @return a new instance of the {@link DecisionEngine} */
  public static DecisionEngine createDecisionEngine() {
    return new DmnScalaDecisionEngine();
  }
}

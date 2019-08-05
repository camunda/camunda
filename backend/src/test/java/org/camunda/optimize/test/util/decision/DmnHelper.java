/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util.decision;

import lombok.experimental.UtilityClass;
import org.camunda.bpm.model.dmn.DmnModelInstance;

@UtilityClass
public class DmnHelper {

  public static DmnModelInstance createSimpleDmnModel(final String decisionKey) {
    // @formatter:off
    return DmnModelGenerator
      .create()
        .decision()
          .decisionDefinitionKey(decisionKey)
          .addOutput("output", DecisionTypeRef.STRING)
        .buildDecision()
      .build();
    // @formatter:on
  }

}

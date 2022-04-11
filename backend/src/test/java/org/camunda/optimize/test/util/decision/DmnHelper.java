/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util.decision;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.bpm.model.dmn.DmnModelInstance;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
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

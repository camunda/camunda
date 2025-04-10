/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.dmn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum DecisionType {
  DECISION_TABLE,
  LITERAL_EXPRESSION,

  UNSPECIFIED,
  UNKNOWN;

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionType.class);

  public static DecisionType fromZeebeDecisionType(final String decisionType) {
    if (decisionType == null) {
      return UNSPECIFIED;
    }
    try {
      return DecisionType.valueOf(decisionType);
    } catch (final IllegalArgumentException ex) {
      LOGGER.error(
          "Decision type not found for value [{}]. UNKNOWN type will be assigned.", decisionType);
      return UNKNOWN;
    }
  }
}

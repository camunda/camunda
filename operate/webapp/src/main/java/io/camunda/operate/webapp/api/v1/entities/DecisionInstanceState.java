/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum DecisionInstanceState {
  FAILED,
  EVALUATED,
  UNKNOWN,
  UNSPECIFIED;

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionInstanceState.class);

  public static DecisionInstanceState getState(
      final io.camunda.webapps.schema.entities.dmn.DecisionInstanceState state) {
    if (state == null) {
      return UNSPECIFIED;
    }
    return switch (state) {
      case FAILED -> FAILED;
      case EVALUATED -> EVALUATED;
      default -> UNKNOWN;
    };
  }

  public static DecisionInstanceState fromString(final String state) {
    if (state == null) {
      return UNSPECIFIED;
    }
    try {
      return DecisionInstanceState.valueOf(state);
    } catch (final IllegalArgumentException ex) {
      LOGGER.error(
          "Decision instance state not found for value [{}]. UNKNOWN type will be assigned.",
          state);
      return UNKNOWN;
    }
  }
}

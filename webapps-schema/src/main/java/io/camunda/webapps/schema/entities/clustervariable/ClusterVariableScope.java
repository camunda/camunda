/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.clustervariable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ClusterVariableScope {
  GLOBAL,
  TENANT,
  UNSPECIFIED;

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterVariableScope.class);

  public static ClusterVariableScope fromProtocol(final String scope) {
    return switch (scope) {
      case "GLOBAL" -> GLOBAL;
      case "TENANT" -> TENANT;
      default -> {
        LOGGER.error(
            "Unknown cluster variable scope received from protocol: {}. Defaulting to UNSPECIFIED.",
            scope);
        yield UNSPECIFIED;
      }
    };
  }
}

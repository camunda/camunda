/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import io.camunda.search.entities.ClusterVariableScope;

public class ClusterVariableUtil {

  public static String generateID(
      final String name, final String tenantId, final ClusterVariableScope scope) {
    return switch (scope) {
      case GLOBAL -> String.format("%s-%s", name, scope);
      case TENANT -> String.format("%s-%s-%s", name, tenantId, scope);
    };
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VariableHelper {

  public static boolean isProcessVariableTypeSupported(final String variableTypeString) {
    return isProcessVariableTypeSupported(
      Optional.ofNullable(variableTypeString).map(VariableType::getTypeForId).orElse(null)
    );
  }

  public static boolean isProcessVariableTypeSupported(final VariableType variableType) {
    return ReportConstants.ALL_SUPPORTED_PROCESS_VARIABLE_TYPES.contains(variableType);
  }

  public static boolean isProcessVariableTypePersistable(final String variableTypeString) {
    return isProcessVariableTypePersistable(
      Optional.ofNullable(variableTypeString).map(VariableType::getTypeForId).orElse(null)
    );
  }

  public static boolean isProcessVariableTypePersistable(final VariableType variableType) {
    return ReportConstants.ALL_PERSISTABLE_PROCESS_VARIABLE_TYPES.contains(variableType);
  }

  public static boolean isDecisionVariableTypeSupported(final String variableTypeString) {
    return isProcessVariableTypeSupported(
      Optional.ofNullable(variableTypeString).map(VariableType::getTypeForId).orElse(null)
    );
  }

  public static boolean isDecisionVariableTypeSupported(final VariableType variableType) {
    return ReportConstants.ALL_SUPPORTED_DECISION_VARIABLE_TYPES.contains(variableType);
  }
}

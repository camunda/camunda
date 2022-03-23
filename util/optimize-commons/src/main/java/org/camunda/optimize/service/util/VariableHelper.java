/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
    return Optional.ofNullable(variableTypeString)
      .map(VariableType::getTypeForId)
      .map(VariableHelper::isProcessVariableTypeSupported)
      .orElse(false);
  }

  public static boolean isProcessVariableTypeSupported(final VariableType variableType) {
    return ReportConstants.ALL_SUPPORTED_PROCESS_VARIABLE_TYPES.contains(variableType);
  }

  public static boolean isDecisionVariableTypeSupported(final String variableTypeString) {
    return Optional.ofNullable(variableTypeString)
      .map(VariableType::getTypeForId)
      .map(VariableHelper::isDecisionVariableTypeSupported)
      .orElse(false);
  }

  public static boolean isDecisionVariableTypeSupported(final VariableType variableType) {
    return ReportConstants.ALL_SUPPORTED_DECISION_VARIABLE_TYPES.contains(variableType);
  }

}

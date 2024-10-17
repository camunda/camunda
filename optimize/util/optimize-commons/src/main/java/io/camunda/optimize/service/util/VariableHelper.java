/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.Optional;

public class VariableHelper {

  private VariableHelper() {}

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

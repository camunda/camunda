/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.experimental.UtilityClass;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.Arrays;
import java.util.Optional;

@UtilityClass
public class VariableHelper {
  public static boolean isVariableTypeSupported(String variableTypeString) {
    return isVariableTypeSupported(
      Optional.ofNullable(variableTypeString).map(VariableType::getTypeForId).orElse(null)
    );
  }

  public static boolean isVariableTypeSupported(VariableType variableType) {
    return Arrays.asList(ReportConstants.ALL_SUPPORTED_VARIABLE_TYPES).contains(variableType);
  }
}

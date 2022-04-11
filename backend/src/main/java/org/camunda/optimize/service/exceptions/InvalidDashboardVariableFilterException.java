/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions;

public class InvalidDashboardVariableFilterException extends OptimizeValidationException {
  public InvalidDashboardVariableFilterException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "invalidDashboardVariableFilter";
  }
}

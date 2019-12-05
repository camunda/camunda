/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions.license;

import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

public abstract class OptimizeLicenseException extends OptimizeRuntimeException {

  public OptimizeLicenseException(final String detailedErrorMessage) {
    super(detailedErrorMessage);
  }
}

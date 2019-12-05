/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions.license;

public class OptimizeInvalidLicenseException extends OptimizeLicenseException {

  public OptimizeInvalidLicenseException(final Exception e) {
    super(e.getMessage());
  }

  public OptimizeInvalidLicenseException(final String detailedErrorMessage) {
    super(detailedErrorMessage);
  }

  @Override
  public String getErrorCode() {
    return "invalidLicenseError";
  }
}

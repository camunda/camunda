/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions.license;

public class OptimizeNoLicenseStoredException extends OptimizeLicenseException {

  public OptimizeNoLicenseStoredException(final String detailedErrorMessage) {
    super(detailedErrorMessage);
  }

  @Override
  public String getErrorCode() {
    return "noLicenseStoredError";
  }
}

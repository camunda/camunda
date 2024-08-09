/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.license;

import org.apache.commons.lang3.StringUtils;

public class CamundaLicense {

  public static final String LICENSE_ENV_VAR_KEY = "CAMUNDA_LICENSE_KEY";
  public static String licenseStr;
  private final EnvironmentVariableReader environmentVariableReader;

  public CamundaLicense() {
    environmentVariableReader = new EnvironmentVariableReader();
  }

  /**
   * Used for testing. Test can mock EnvironmentVariableReader to test out different environment
   * variable values
   */
  public CamundaLicense(final EnvironmentVariableReader environmentVariableReader) {
    this.environmentVariableReader = environmentVariableReader;
  }

  public boolean isValid() {
    if (!isLicenseInitialized()) {
      initializeLicenseCache();
    }

    // TODO - return a real computed value, but always return true for now
    return true;
  }

  private boolean isLicenseInitialized() {
    return StringUtils.isNotBlank(licenseStr);
  }

  private void initializeLicenseCache() {
    licenseStr = environmentVariableReader.getEnvironmentVariableValue(LICENSE_ENV_VAR_KEY);
    // TODO - some license computation here
  }
}

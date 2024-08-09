/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.license;

import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.camunda.bpm.licensecheck.LicenseKeyImpl;

public class CamundaLicense {

  public static final String LICENSE_ENV_VAR_KEY = "CAMUNDA_LICENSE_KEY";
  public static String licenseStr;
  public static boolean isLicenseValid;

  public CamundaLicense() {}

  public boolean isValid() {
    if (!isLicenseInitialized()) {
      initializeStoredLicense();
    }

    return isLicenseValid;
  }

  protected boolean isLicenseInitialized() {
    return StringUtils.isNotBlank(licenseStr);
  }

  public void initializeStoredLicense() {
    licenseStr = getEnvironmentVariableValue(LICENSE_ENV_VAR_KEY);
    isLicenseValid = determineLicenseValidity(licenseStr);
  }

  protected boolean determineLicenseValidity(final String licenseStr) {
    try {
      final LicenseKey license = getLicenseKey(licenseStr);
      license.validate(); // this method logs the license status
      return true;
    } catch (final Exception e) {
      return false;
    }
  }

  protected LicenseKey getLicenseKey(final String licenseStr) throws InvalidLicenseException {
    return new LicenseKeyImpl(licenseStr);
  }

  protected String getEnvironmentVariableValue(final String envVarName) {
    return System.getenv().getOrDefault(envVarName, "");
  }
}

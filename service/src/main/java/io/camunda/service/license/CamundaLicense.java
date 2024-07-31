/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.license;

import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.camunda.bpm.licensecheck.LicenseKeyImpl;

public class CamundaLicense {

  public String license;
  public boolean isValid;
  private volatile boolean isInitialized;

  public CamundaLicense(final String license) {
    this.license = license;
  }

  public boolean isValid() {
    initializeLicenseKey();
    return isValid;
  }

  protected void initializeLicenseKey() {
    if (!isInitialized) {
      synchronized (this) {
        if (!isInitialized) {
          isValid = determineLicenseValidity(license);
          isInitialized = true;
        }
      }
    }
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
}

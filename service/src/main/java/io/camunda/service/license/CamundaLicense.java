/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.license;

import io.camunda.zeebe.util.VisibleForTesting;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.camunda.bpm.licensecheck.LicenseKeyImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaLicense {

  public static final String CAMUNDA_LICENSE_ENV_VAR_KEY = "CAMUNDA_LICENSE_KEY";
  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaLicense.class);
  private boolean isValid;
  private LicenseType licenseType;
  private boolean isInitialized;

  @VisibleForTesting
  protected CamundaLicense() {}

  public CamundaLicense(final String license) {
    initializeWithLicense(license);
  }

  public synchronized boolean isValid() {
    return isValid;
  }

  public synchronized LicenseType getLicenseType() {
    return licenseType;
  }

  public synchronized void initializeWithLicense(final String license) {
    if (isInitialized) {
      return;
    }

    if (license != null && !license.isBlank()) {
      validateLicense(license);
    } else {
      isValid = false;
      licenseType = LicenseType.UNKNOWN;
      LOGGER.warn(
          "No license detected when one is expected. Please provide a license through the "
              + CAMUNDA_LICENSE_ENV_VAR_KEY
              + " environment variable.");
    }

    isInitialized = true;
  }

  private void validateLicense(final String licenseStr) {
    try {
      final LicenseKey licenseKey = getLicenseKey(licenseStr);
      licenseKey.validate(); // this method logs the license status

      licenseType = LicenseType.get(licenseKey.getProperties().get("licenseType"));

      if (LicenseType.UNKNOWN.equals(licenseType)) {
        LOGGER.warn(
            "Expected a valid licenseType property on the Camunda License, but none were found.");
        isValid = false;
      } else {
        isValid = true;
      }

      return;
    } catch (final InvalidLicenseException e) {
      LOGGER.warn(
          "Expected a valid license when determining license validity, but encountered an invalid one instead. ",
          e);
    } catch (final Exception e) {
      LOGGER.warn(
          "Expected to determine the validity of the license, but the following unexpected error was encountered: ",
          e);
    }

    licenseType = LicenseType.UNKNOWN;
    isValid = false;
  }

  @VisibleForTesting
  protected LicenseKey getLicenseKey(final String licenseStr) throws InvalidLicenseException {
    return new LicenseKeyImpl(licenseStr);
  }
}

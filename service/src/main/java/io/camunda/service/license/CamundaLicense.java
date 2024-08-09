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

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaLicense.class);
  private boolean isValid;
  private String licenseType;
  private boolean isInitialized;

  @VisibleForTesting
  protected CamundaLicense() {}

  public CamundaLicense(final String license) {
    initializeWithLicense(license);
  }

  public synchronized boolean isValid() {
    return isValid;
  }

  public synchronized String getLicenseTypePropertyFromLicense() {
    return licenseType;
  }

  public synchronized void initializeWithLicense(final String license) {
    if (!isInitialized) {
      isValid = determineLicenseValidity(license);
      licenseType = getLicenseTypePropertyFromLicense(license);

      isInitialized = true;
    }
  }

  /**
   * Camunda licenses will have a license type property, fetch that out of the license and return
   * the value
   *
   * <p>Self-managed mode is any other possibility. (ex, blank license, prop missing, etc)
   */
  private String getLicenseTypePropertyFromLicense(final String licenseStr) {
    try {
      final LicenseKey licenseKey = getLicenseKey(licenseStr);
      if (licenseKey.getProperties().containsKey("licenseType")) {
        return licenseKey.getProperties().get("licenseType");
      } else {
        LOGGER.error(
            "Expected a licenseType property on the Camunda License, but none were found.");
        return "unknown";
      }
    } catch (final InvalidLicenseException e) {
      LOGGER.error(
          "Expected a valid license when determining the type of license, but encountered an invalid one instead. ",
          e);
      return "unknown";
    } catch (final Exception e) {
      LOGGER.error(
          "Expected to determine the license type of the license, but the following unexpected error was encountered: ",
          e);
      return "unknown";
    }
  }

  private boolean determineLicenseValidity(final String licenseStr) {
    try {
      final LicenseKey license = getLicenseKey(licenseStr);
      license.validate(); // this method logs the license status
      return true;
    } catch (final InvalidLicenseException e) {
      LOGGER.error(
          "Expected a valid license when determining license validity, but encountered an invalid one instead. ",
          e);
      return false;
    } catch (final Exception e) {
      LOGGER.error(
          "Expected to validate a the Camunda license, but the following unexpected error was encountered: ",
          e);
      return false;
    }
  }

  @VisibleForTesting
  protected LicenseKey getLicenseKey(final String licenseStr) throws InvalidLicenseException {
    return new LicenseKeyImpl(licenseStr);
  }
}

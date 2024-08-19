/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.license;

import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.camunda.bpm.licensecheck.LicenseKeyImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaLicense {

  public static final String CAMUNDA_LICENSE_ENV_VAR_KEY = "CAMUNDA_LICENSE_KEY";
  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaLicense.class);
  private static final String UNKNOWN_LICENSE_TYPE = "unknown";
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
      isValid = determineLicenseValidity(license);
      licenseType = getLicenseTypeFromProperty(license);
    } else {
      isValid = false;
      licenseType = LicenseType.UNKNOWN;
      LOGGER.error(
          "No license detected when one is expected. Please provide a license through the "
              + CAMUNDA_LICENSE_ENV_VAR_KEY
              + " environment variable.");
    }

    isInitialized = true;
  }

  /**
   * Camunda licenses will have a license type property, fetch that out of the license and return
   * the value
   *
   * <p>Self-managed mode is any other possibility. (ex, blank license, prop missing, etc)
   */
  private LicenseType getLicenseTypeFromProperty(final String licenseStr) {
    try {
      final LicenseKey licenseKey = getLicenseKey(licenseStr);
      final String licenseType =
          licenseKey.getProperties().getOrDefault("licenseType", UNKNOWN_LICENSE_TYPE);

      if (UNKNOWN_LICENSE_TYPE.equals(licenseType)) {
        LOGGER.error(
            "Expected a licenseType property on the Camunda License, but none were found.");
      }

      return LicenseType.get(licenseType);
    } catch (final InvalidLicenseException e) {
      LOGGER.error(
          "Expected a valid license when determining the type of license, but encountered an invalid one instead. ",
          e);
    } catch (final Exception e) {
      LOGGER.error(
          "Expected to determine the license type of the license, but the following unexpected error was encountered: ",
          e);
    }
    return LicenseType.UNKNOWN;
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

  public enum LicenseType {
    SAAS("saas"),
    SELFMANAGED("self-managed"),
    UNKNOWN("unknown");
    private static final Map<String, LicenseType> ENUM_MAP;

    static {
      final Map<String, LicenseType> map = new HashMap<>();
      for (final LicenseType instance : LicenseType.values()) {
        map.put(instance.getName().toLowerCase(), instance);
      }
      ENUM_MAP = Collections.unmodifiableMap(map);
    }

    private final String name;

    LicenseType(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public static LicenseType get(final String name) {
      return ENUM_MAP.getOrDefault(name.toLowerCase(), LicenseType.UNKNOWN);
    }
  }
}

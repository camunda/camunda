/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.license;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.camunda.bpm.licensecheck.LicenseKeyImpl;
import org.camunda.bpm.licensecheck.LicenseType;
import org.camunda.optimize.dto.optimize.query.LicenseInformationResponseDto;
import org.camunda.optimize.service.exceptions.license.OptimizeInvalidLicenseException;
import org.camunda.optimize.service.exceptions.license.OptimizeNoLicenseStoredException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public abstract class LicenseManager {

  private static final String OPTIMIZE_LICENSE_FILE = "OptimizeLicense.txt";
  protected final String licenseDocumentId = "license";

  @Setter
  protected String optimizeLicense;
  private final Map<String, String> requiredUnifiedKeyMap = Collections.singletonMap("optimize", "true");

  public abstract void storeLicense(String licenseAsString);
  protected abstract Optional<String> retrieveStoredOptimizeLicense();

  @PostConstruct
  public void init() {
    retrieveStoredOptimizeLicense().ifPresentOrElse(
      this::setOptimizeLicense,
      ()-> {
        log.info("No license stored in the DB, trying to read from file");
        try {
          readFileToString().ifPresent(this::storeLicense);
        } catch (IOException e) {
          log.warn("Not able to read optimize license from file", e);
        }
      });
  }

  public Optional<String> getOptimizeLicense() {
    return Optional.ofNullable(optimizeLicense);
  }

  public LicenseInformationResponseDto validateLicenseStoredInOptimize() {
    validateLicenseExists();
    return validateOptimizeLicense(optimizeLicense);
  }

  public LicenseInformationResponseDto validateOptimizeLicense(String license) {
    if (StringUtils.isBlank(license)) {
      throw new OptimizeInvalidLicenseException(
        "Could not validate given license. Please try to provide another license!");
    }

    try {
      LicenseKey licenseKey = new LicenseKeyImpl(license);
      // check that the license key is a legacy key
      if (licenseKey.getLicenseType() == LicenseType.OPTIMIZE) {
        licenseKey.validate();
      } else {
        licenseKey.validate(requiredUnifiedKeyMap);
      }
      return licenseKeyToDto(licenseKey);
    } catch (InvalidLicenseException e) {
      throw new OptimizeInvalidLicenseException(e);
    }
  }

  private Optional<String> readFileToString() throws IOException {
    InputStream inputStream = this.getClass()
      .getClassLoader()
      .getResourceAsStream(OPTIMIZE_LICENSE_FILE);
    if (inputStream == null) {
      log.warn("There was an error reading the Optimize license from " + OPTIMIZE_LICENSE_FILE);
      return Optional.empty();
    }

    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = inputStream.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    return Optional.of(result.toString(StandardCharsets.UTF_8.name()));
  }

  private void validateLicenseExists() {
    if (optimizeLicense == null) {
      log.info("""
                ############### Heads up ################
                You tried to access Optimize, but no valid license could be
                found. Please enter a valid license key!  If you already have 
                a valid key you can have a look here, how to add it to Optimize:
            
                https://docs.camunda.io/docs/next/self-managed/optimize-deployment/configuration/optimize-license/ 
            
                In case you don't have a valid license, feel free to contact us at:
            
                https://camunda.com/contact/
            
                You will now be redirected to the license page...
                """);

      throw new OptimizeNoLicenseStoredException(
        "No license stored in Optimize. Please provide a valid Optimize license");
    }
  }

  private LicenseInformationResponseDto licenseKeyToDto(LicenseKey licenseKey) {
    LicenseInformationResponseDto dto = new LicenseInformationResponseDto();
    dto.setCustomerId(licenseKey.getCustomerId());
    dto.setUnlimited(licenseKey.isUnlimited());
    if (!licenseKey.isUnlimited()) {
      dto.setValidUntil(OffsetDateTime.ofInstant(licenseKey.getValidUntil().toInstant(), ZoneId.systemDefault()));
    }
    return dto;
  }

}

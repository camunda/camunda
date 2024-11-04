/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import io.camunda.optimize.license.CamundaLicense;
import io.camunda.optimize.license.LicenseType;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class CamundaLicenseService {

  private final CamundaLicense license;

  public CamundaLicenseService() {
    final String camundaLicense = System.getenv(CamundaLicense.CAMUNDA_LICENSE_ENV_VAR_KEY);
    license = new CamundaLicense(camundaLicense);
  }

  public boolean isCamundaLicenseValid() {
    return license.isValid();
  }

  public LicenseType getCamundaLicenseType() {
    return license.getLicenseType();
  }

  public boolean isCommercialCamundaLicense() {
    return license.isCommercial();
  }

  public OffsetDateTime getCamundaLicenseExpirationDate() {
    return license.expiresAt();
  }
}

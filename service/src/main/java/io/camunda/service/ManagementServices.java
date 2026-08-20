/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.service.license.CamundaLicense;
import io.camunda.service.license.LicenseType;
import java.time.OffsetDateTime;

public final class ManagementServices {

  private final CamundaLicense license;

  public ManagementServices(final CamundaLicense license) {
    this.license = license;
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

  public OffsetDateTime getCamundaLicenseExpiresAt() {
    return license.expiresAt();
  }
}

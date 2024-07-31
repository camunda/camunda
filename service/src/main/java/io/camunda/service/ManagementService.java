/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.service.license.CamundaLicense;

public final class ManagementService {

  private CamundaLicense camundaLicense;

  public ManagementService withLicense(final CamundaLicense license) {
    camundaLicense = license;
    return this;
  }

  public boolean isCamundaLicenseValid() {
    return camundaLicense != null && camundaLicense.isValid();
  }
}

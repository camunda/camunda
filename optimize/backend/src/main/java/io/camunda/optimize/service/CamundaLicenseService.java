/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import io.camunda.service.license.CamundaLicense;
import org.springframework.stereotype.Component;

@Component
public class CamundaLicenseService {

  private final CamundaLicense license;

  public CamundaLicenseService() {
    final String camundaLicense = System.getenv("CAMUNDA_LICENSE_KEY");
    license = new CamundaLicense(camundaLicense);
  }

  public boolean isCamundaLicenseValid() {
    return license.isValid();
  }

  public String getCamundaLicenseType() {
    return license.getLicenseType();
  }
}

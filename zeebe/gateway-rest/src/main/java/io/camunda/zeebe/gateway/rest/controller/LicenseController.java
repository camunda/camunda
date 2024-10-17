/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.ManagementServices;
import io.camunda.zeebe.gateway.protocol.rest.LicenseResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping(path = {"/v2"})
public class LicenseController {

  private final ManagementServices managementServices;

  public LicenseController(final ManagementServices managementServices) {
    this.managementServices = managementServices;
  }

  @GetMapping(path = "/license", produces = MediaType.APPLICATION_JSON_VALUE)
  public LicenseResponse get() {
    final LicenseResponse response = new LicenseResponse();
    response.setValidLicense(managementServices.isCamundaLicenseValid());
    response.setLicenseType(managementServices.getCamundaLicenseType().getName());

    return response;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.protocol.model.LicenseResponse;
import io.camunda.service.ManagementServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping(path = {"/v2"})
public class LicenseController {

  @VisibleForTesting
  public static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  private final ManagementServices managementServices;

  public LicenseController(final ManagementServices managementServices) {
    this.managementServices = managementServices;
  }

  @CamundaGetMapping(path = "/license")
  public LicenseResponse get() {
    final LicenseResponse response = new LicenseResponse();
    response.setValidLicense(managementServices.isCamundaLicenseValid());
    response.setLicenseType(managementServices.getCamundaLicenseType().getName());
    response.setIsCommercial(managementServices.isCommercialCamundaLicense());
    final OffsetDateTime expirationDate = managementServices.getCamundaLicenseExpiresAt();
    response.setExpiresAt(
        expirationDate == null ? null : DATE_TIME_FORMATTER.format(expirationDate));

    return response;
  }
}

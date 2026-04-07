/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedLicenseResponseStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.ManagementServices;
import io.camunda.zeebe.gateway.rest.controller.generated.LicenseServiceAdapter;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultLicenseServiceAdapter implements LicenseServiceAdapter {

  @VisibleForTesting
  public static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  private final ManagementServices managementServices;

  public DefaultLicenseServiceAdapter(final ManagementServices managementServices) {
    this.managementServices = managementServices;
  }

  @Override
  public ResponseEntity<Object> getLicense(final CamundaAuthentication authentication) {
    final OffsetDateTime expirationDate = managementServices.getCamundaLicenseExpiresAt();
    final var response =
        new GeneratedLicenseResponseStrictContract(
            managementServices.isCamundaLicenseValid(),
            managementServices.getCamundaLicenseType().getName(),
            managementServices.isCommercialCamundaLicense(),
            expirationDate == null ? null : DATE_TIME_FORMATTER.format(expirationDate));
    return ResponseEntity.ok(response);
  }
}

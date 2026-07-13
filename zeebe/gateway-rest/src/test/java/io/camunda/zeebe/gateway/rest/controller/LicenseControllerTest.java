/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.LicenseService;
import io.camunda.service.license.LicenseType;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = LicenseController.class)
public class LicenseControllerTest extends RestControllerTest {

  static final String LICENSE_URL = "/v2/license";
  static final String PT_LICENSE_URL = "/physical-tenants/foo/v2/license";

  static final String EXPECTED_LICENSE_RESPONSE =
      """
      {
          "validLicense": true,
          "licenseType": "saas",
          "isCommercial": true,
          "expiresAt": "2024-10-29T15:14:13Z"
      }""";

  static final String EXPECTED_LICENSE_RESPONSE_NO_EXPIRATION =
      """
      {
          "validLicense": true,
          "licenseType": "saas",
          "isCommercial": true,
          "expiresAt": null
      }""";

  @MockitoBean LicenseService licenseService;

  @Test
  void shouldReturnProperSaaSResponse() {
    // given
    when(licenseService.isCamundaLicenseValid()).thenReturn(true);
    when(licenseService.getCamundaLicenseType()).thenReturn(LicenseType.SAAS);
    when(licenseService.isCommercialCamundaLicense()).thenReturn(true);
    when(licenseService.getCamundaLicenseExpiresAt())
        .thenReturn(OffsetDateTime.parse("2024-10-29T15:14:13Z"));

    // when / then
    webClient
        .get()
        .uri(LICENSE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_LICENSE_RESPONSE, JsonCompareMode.STRICT);

    verify(licenseService).isCamundaLicenseValid();
    verify(licenseService).getCamundaLicenseType();
    verify(licenseService).isCommercialCamundaLicense();
    verify(licenseService).getCamundaLicenseExpiresAt();
  }

  @Test
  void shouldReturnLicenseForPhysicalTenantPath() {
    // given
    when(licenseService.isCamundaLicenseValid()).thenReturn(true);
    when(licenseService.getCamundaLicenseType()).thenReturn(LicenseType.SAAS);
    when(licenseService.isCommercialCamundaLicense()).thenReturn(true);
    when(licenseService.getCamundaLicenseExpiresAt())
        .thenReturn(OffsetDateTime.parse("2024-10-29T15:14:13Z"));

    // when / then
    webClient
        .get()
        .uri(PT_LICENSE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_LICENSE_RESPONSE, JsonCompareMode.STRICT);

    verify(licenseService).isCamundaLicenseValid();
    verify(licenseService).getCamundaLicenseType();
    verify(licenseService).isCommercialCamundaLicense();
    verify(licenseService).getCamundaLicenseExpiresAt();
  }

  @Test
  void shouldReturnWithoutExpirationDateWhenThereIsNoExpirationOnLicense() {
    // given
    when(licenseService.isCamundaLicenseValid()).thenReturn(true);
    when(licenseService.getCamundaLicenseType()).thenReturn(LicenseType.SAAS);
    when(licenseService.isCommercialCamundaLicense()).thenReturn(true);
    when(licenseService.getCamundaLicenseExpiresAt()).thenReturn(null);

    // when / then
    webClient
        .get()
        .uri(LICENSE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_LICENSE_RESPONSE_NO_EXPIRATION, JsonCompareMode.STRICT);

    verify(licenseService).isCamundaLicenseValid();
    verify(licenseService).getCamundaLicenseType();
    verify(licenseService).isCommercialCamundaLicense();
    verify(licenseService).getCamundaLicenseExpiresAt();
  }
}

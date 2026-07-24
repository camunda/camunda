/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.ExportingServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ExportingController.class)
public class ExportingControllerTest extends RestControllerTest {

  private static final String PAUSE_URL = "/v2/exporting/pause";
  private static final String RESUME_URL = "/v2/exporting/resume";

  @MockitoBean private ExportingServices exportingServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.exportingServices(any())).thenReturn(exportingServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void pauseExportingShouldReturnNoContent() {
    // given
    when(exportingServices.pauseExporting(eq(false), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .post()
        .uri(PAUSE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(exportingServices).pauseExporting(eq(false), any());
  }

  @Test
  void pauseExportingShouldForwardSoftQueryParam() {
    // given
    when(exportingServices.pauseExporting(anyBoolean(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .post()
        .uri(PAUSE_URL.concat("?soft=true"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(exportingServices).pauseExporting(eq(true), any());
  }

  @Test
  void pauseExportingShouldReturnServiceUnavailableOnIncompleteTopology() {
    // given
    when(exportingServices.pauseExporting(anyBoolean(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Topology is incomplete", Status.UNAVAILABLE)));

    // when - then
    webClient
        .post()
        .uri(PAUSE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void pauseExportingShouldReturnForbiddenWhenUnauthorized() {
    // given
    when(exportingServices.pauseExporting(anyBoolean(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Unauthorized to perform operation", Status.FORBIDDEN)));

    // when - then
    webClient
        .post()
        .uri(PAUSE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void resumeExportingShouldReturnNoContent() {
    // given
    when(exportingServices.resumeExporting(any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .post()
        .uri(RESUME_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(exportingServices).resumeExporting(any());
  }
}

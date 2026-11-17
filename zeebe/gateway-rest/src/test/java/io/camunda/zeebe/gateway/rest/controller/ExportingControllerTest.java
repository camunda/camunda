/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
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
import io.camunda.zeebe.dynamic.config.api.ExportingStatus;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(ExportingController.class)
public class ExportingControllerTest extends RestControllerTest {

  private static final String STATUS_PATH = "/exporting";
  private static final String PAUSE_PATH = "/exporting/pause";
  private static final String RESUME_PATH = "/exporting/resume";

  @MockitoBean private ExportingServices exportingServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.exportingServices(any())).thenReturn(exportingServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2", "/physical-tenants/default/v2"})
  void pauseExportingShouldReturnNoContentAndTargetDefaultTenant(final String baseUrl) {
    // given
    when(exportingServices.pauseExporting(eq(false), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .post()
        .uri(baseUrl + PAUSE_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // the unprefixed and the /physical-tenants/default/ routes both resolve to the default PT
    verify(serviceRegistry).exportingServices(DEFAULT_PHYSICAL_TENANT_ID);
    verify(exportingServices).pauseExporting(eq(false), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2", "/physical-tenants/default/v2"})
  void pauseExportingShouldForwardSoftQueryParam(final String baseUrl) {
    // given
    when(exportingServices.pauseExporting(anyBoolean(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .post()
        .uri(baseUrl + PAUSE_PATH + "?soft=true")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(serviceRegistry).exportingServices(DEFAULT_PHYSICAL_TENANT_ID);
    verify(exportingServices).pauseExporting(eq(true), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2", "/physical-tenants/default/v2"})
  void pauseExportingShouldReturnServiceUnavailableOnIncompleteTopology(final String baseUrl) {
    // given
    when(exportingServices.pauseExporting(anyBoolean(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Topology is incomplete", Status.UNAVAILABLE)));

    // when - then
    webClient
        .post()
        .uri(baseUrl + PAUSE_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2", "/physical-tenants/default/v2"})
  void pauseExportingShouldReturnForbiddenWhenUnauthorized(final String baseUrl) {
    // given
    when(exportingServices.pauseExporting(anyBoolean(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Unauthorized to perform operation", Status.FORBIDDEN)));

    // when - then
    webClient
        .post()
        .uri(baseUrl + PAUSE_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2", "/physical-tenants/default/v2"})
  void resumeExportingShouldReturnNoContentAndTargetDefaultTenant(final String baseUrl) {
    // given
    when(exportingServices.resumeExporting(any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .post()
        .uri(baseUrl + RESUME_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(serviceRegistry).exportingServices(DEFAULT_PHYSICAL_TENANT_ID);
    verify(exportingServices).resumeExporting(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2", "/physical-tenants/default/v2"})
  void resumeExportingShouldReturnServiceUnavailableOnIncompleteTopology(final String baseUrl) {
    // given
    when(exportingServices.resumeExporting(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Topology is incomplete", Status.UNAVAILABLE)));

    // when - then
    webClient
        .post()
        .uri(baseUrl + RESUME_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2", "/physical-tenants/default/v2"})
  void resumeExportingShouldReturnForbiddenWhenUnauthorized(final String baseUrl) {
    // given
    when(exportingServices.resumeExporting(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Unauthorized to perform operation", Status.FORBIDDEN)));

    // when - then
    webClient
        .post()
        .uri(baseUrl + RESUME_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2", "/physical-tenants/default/v2"})
  void getExportingStatusShouldReturnStatusAndTargetDefaultTenant(final String baseUrl) {
    // given
    when(exportingServices.getExportingStatus(any()))
        .thenReturn(CompletableFuture.completedFuture(ExportingStatus.SOFT_PAUSED));

    // when - then
    webClient
        .get()
        .uri(baseUrl + STATUS_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("{\"status\":\"SOFT_PAUSED\"}", JsonCompareMode.STRICT);

    // the unprefixed and the /physical-tenants/default/ routes both resolve to the default PT
    verify(serviceRegistry).exportingServices(DEFAULT_PHYSICAL_TENANT_ID);
    verify(exportingServices).getExportingStatus(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2", "/physical-tenants/default/v2"})
  void getExportingStatusShouldReturnServiceUnavailableOnIncompleteTopology(final String baseUrl) {
    // given
    when(exportingServices.getExportingStatus(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Topology is incomplete", Status.UNAVAILABLE)));

    // when - then
    webClient
        .get()
        .uri(baseUrl + STATUS_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2", "/physical-tenants/default/v2"})
  void getExportingStatusShouldReturnForbiddenWhenUnauthorized(final String baseUrl) {
    // given
    when(exportingServices.getExportingStatus(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Unauthorized to perform operation", Status.FORBIDDEN)));

    // when - then
    webClient
        .get()
        .uri(baseUrl + STATUS_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden();
  }
}

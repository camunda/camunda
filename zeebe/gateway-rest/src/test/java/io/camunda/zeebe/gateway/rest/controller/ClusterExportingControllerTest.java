/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.ClusterExportingServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.dynamic.config.api.ExportingStatus;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(ClusterExportingController.class)
class ClusterExportingControllerTest extends RestControllerTest {

  static final String STATUS_URL = "/cluster/v2/exporting";
  static final String PAUSE_URL = "/cluster/v2/exporting/pause";
  static final String RESUME_URL = "/cluster/v2/exporting/resume";

  @MockitoBean ClusterExportingServices clusterExportingServices;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.clusterExportingServices()).thenReturn(clusterExportingServices);
  }

  @Test
  void shouldReturnStatusOfTheWholeCluster() {
    // given
    when(clusterExportingServices.getExportingStatus())
        .thenReturn(CompletableFuture.completedFuture(ExportingStatus.SOFT_PAUSED));

    // when / then
    webClient
        .get()
        .uri(STATUS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("{\"status\":\"SOFT_PAUSED\"}", JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnServiceUnavailableWhenTopologyIsIncomplete() {
    // given
    when(clusterExportingServices.getExportingStatus())
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Topology is incomplete", Status.UNAVAILABLE)));

    // when / then
    webClient
        .get()
        .uri(STATUS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void shouldReturnBadRequestOnUnknownExporterPhase() {
    // given
    when(clusterExportingServices.getExportingStatus())
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("JSON property was invalid", Status.INVALID_ARGUMENT)));

    // when / then
    webClient
        .get()
        .uri(STATUS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldPauseEveryTenantAndReturnNoContent() {
    // given
    when(clusterExportingServices.pauseExporting(eq(false)))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when / then
    webClient
        .post()
        .uri(PAUSE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(clusterExportingServices).pauseExporting(eq(false));
  }

  @Test
  void shouldForwardSoftQueryParamOnPause() {
    // given
    when(clusterExportingServices.pauseExporting(anyBoolean()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when / then
    webClient
        .post()
        .uri(PAUSE_URL + "?soft=true")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(clusterExportingServices).pauseExporting(eq(true));
  }

  @Test
  void shouldReturnServiceUnavailableOnPauseWhenTopologyIsIncomplete() {
    // given
    when(clusterExportingServices.pauseExporting(anyBoolean()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Topology is incomplete", Status.UNAVAILABLE)));

    // when / then
    webClient
        .post()
        .uri(PAUSE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void shouldResumeEveryTenantAndReturnNoContent() {
    // given
    when(clusterExportingServices.resumeExporting())
        .thenReturn(CompletableFuture.completedFuture(null));

    // when / then
    webClient
        .post()
        .uri(RESUME_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(clusterExportingServices).resumeExporting();
  }

  @Test
  void shouldReturnServiceUnavailableOnResumeWhenTopologyIsIncomplete() {
    // given
    when(clusterExportingServices.resumeExporting())
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Topology is incomplete", Status.UNAVAILABLE)));

    // when / then
    webClient
        .post()
        .uri(RESUME_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void shouldNotRegisterPhysicalTenantPrefixedRoute() {
    // when / then — @ClusterScoped must suppress the PT-prefixed sibling route
    webClient
        .get()
        .uri("/physical-tenants/default/cluster/v2/exporting")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();
  }
}

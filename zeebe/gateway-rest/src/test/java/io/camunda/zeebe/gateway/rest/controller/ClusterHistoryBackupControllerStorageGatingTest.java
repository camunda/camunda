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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.service.ClusterHistoryBackupServices;
import io.camunda.service.exception.SecondaryStorageDegradedException;
import io.camunda.service.exception.SecondaryStorageTypeNotSupportedException;
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.GlobalControllerExceptionHandler;
import io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Asserts that {@link ClusterHistoryBackupController} is gated to Elasticsearch and OpenSearch, and
 * that its readiness gate is the cluster-scoped one.
 *
 * <p>Deliberately not a {@code @WebMvcTest} slice: {@code RestControllerTest} replaces {@link
 * SecondaryStorageInterceptor} with a mock that permits every request, so a status assertion there
 * would only be testing that mock. This wires the real interceptor to the real controller, which is
 * what catches the annotations going missing.
 */
class ClusterHistoryBackupControllerStorageGatingTest {

  private static final String BASE_URL = "/cluster/v2/backups/history";

  @Test
  void shouldRejectAStorageThatCannotServeHistoryBackups() throws Exception {
    // given
    final var mockMvc = mockMvcFor(DatabaseType.RDBMS, SecondaryStorageReadiness.ALWAYS_READY);

    // when - then
    mockMvc
        .perform(get(BASE_URL))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("FORBIDDEN"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    SecondaryStorageTypeNotSupportedException
                        .UNSUPPORTED_SECONDARY_STORAGE_TYPE_MESSAGE
                        .formatted("elasticsearch, opensearch", "rdbms")));
  }

  @Test
  void shouldRejectWhenTheClusterHasNoSecondaryStorage() throws Exception {
    // given
    final var mockMvc = mockMvcFor(DatabaseType.NONE, SecondaryStorageReadiness.ALWAYS_READY);

    // when - then
    mockMvc
        .perform(get(BASE_URL))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.detail")
                .value(SecondaryStorageUnavailableException.NO_SECONDARY_STORAGE_MESSAGE));
  }

  /**
   * The cluster-wide surface is what an operator reaches for while a physical tenant is unusable,
   * so one unready tenant must not close it. Readiness means "schema initialized", a state a tenant
   * can fail to leave permanently.
   */
  @Test
  void shouldServeTheClusterWideEndpointWhileOnlySomePhysicalTenantsAreReady() throws Exception {
    // given a readiness that reports no individual tenant ready, but the cluster as a whole is
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.isReady(any())).thenReturn(false);
    when(readiness.anyReady()).thenReturn(true);
    final var mockMvc = mockMvcFor(DatabaseType.ELASTICSEARCH, readiness);

    // when - then
    mockMvc.perform(get(BASE_URL)).andExpect(request().asyncStarted());
  }

  @Test
  void shouldRejectWhenNoPhysicalTenantIsReady() throws Exception {
    // given
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.anyReady()).thenReturn(false);
    final var mockMvc = mockMvcFor(DatabaseType.ELASTICSEARCH, readiness);

    // when - then
    mockMvc
        .perform(get(BASE_URL))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "5"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    SecondaryStorageDegradedException.CLUSTER_SECONDARY_STORAGE_DEGRADED_MESSAGE));
  }

  /**
   * Asserts the request started async rather than its status: the handler returns a {@code
   * CompletableFuture}, so an un-dispatched 200 would also be the result of never reaching it.
   */
  @ParameterizedTest
  @EnumSource(
      value = DatabaseType.class,
      names = {"ELASTICSEARCH", "OPENSEARCH"})
  void shouldReachTheHandlerOnADocumentStorage(final DatabaseType storageType) throws Exception {
    // given
    final var mockMvc = mockMvcFor(storageType, SecondaryStorageReadiness.ALWAYS_READY);

    // when - then
    mockMvc.perform(get(BASE_URL)).andExpect(request().asyncStarted());
  }

  private static MockMvc mockMvcFor(
      final DatabaseType secondaryStorageType, final SecondaryStorageReadiness readiness) {
    final var clusterHistoryBackupServices = mock(ClusterHistoryBackupServices.class);
    when(clusterHistoryBackupServices.listBackups(any(), any(), anyBoolean()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    final var serviceRegistry = mock(ServiceRegistry.class);
    when(serviceRegistry.clusterHistoryBackupServices()).thenReturn(clusterHistoryBackupServices);

    final var interceptor =
        new SecondaryStorageInterceptor(tenantId -> secondaryStorageType, readiness);
    return MockMvcBuilders.standaloneSetup(new ClusterHistoryBackupController(serviceRegistry))
        .addInterceptors(interceptor)
        .setControllerAdvice(new GlobalControllerExceptionHandler())
        .build();
  }
}

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.HistoryBackupServices;
import io.camunda.service.exception.SecondaryStorageTypeNotSupportedException;
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.GlobalControllerExceptionHandler;
import io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Asserts that {@link HistoryBackupController} is actually gated to Elasticsearch and OpenSearch.
 *
 * <p>Deliberately not a {@code @WebMvcTest} slice: {@code RestControllerTest} replaces {@link
 * SecondaryStorageInterceptor} with a mock that permits every request, so a status assertion there
 * would only be testing that mock. This wires the real interceptor to the real controller, which is
 * what catches the annotation going missing.
 */
class HistoryBackupControllerStorageGatingTest {

  @Test
  void shouldRejectAStorageThatCannotServeHistoryBackups() throws Exception {
    // given
    final var mockMvc = mockMvcFor(DatabaseType.RDBMS);

    // when - then
    mockMvc
        .perform(get("/v2/backups/history"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("FORBIDDEN"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    SecondaryStorageTypeNotSupportedException
                        .UNSUPPORTED_SECONDARY_STORAGE_TYPE_MESSAGE
                        .formatted("elasticsearch, opensearch", "rdbms")));
  }

  /**
   * A tenant with no secondary storage is refused with the same 403 but the "none configured"
   * detail, which is the generic {@link SecondaryStorageInterceptor} behaviour rather than anything
   * this endpoint declares.
   */
  @Test
  void shouldRejectWhenTheTenantHasNoSecondaryStorage() throws Exception {
    // given
    final var mockMvc = mockMvcFor(DatabaseType.NONE);

    // when - then
    mockMvc
        .perform(get("/v2/backups/history"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("FORBIDDEN"))
        .andExpect(
            jsonPath("$.detail")
                .value(SecondaryStorageUnavailableException.NO_SECONDARY_STORAGE_MESSAGE));
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
    final var mockMvc = mockMvcFor(storageType);

    // when - then
    mockMvc.perform(get("/v2/backups/history")).andExpect(request().asyncStarted());
  }

  /** The endpoints marked {@code availableWhenRecovering}, as method and path. */
  static Stream<Arguments> availableWhenRecoveringEndpoints() {
    return Stream.of(
        Arguments.of(HttpMethod.POST, "/v2/backups/history"),
        Arguments.of(HttpMethod.GET, "/v2/backups/history"),
        Arguments.of(HttpMethod.GET, "/v2/backups/history/1"),
        Arguments.of(HttpMethod.DELETE, "/v2/backups/history/1"));
  }

  @ParameterizedTest
  @MethodSource("availableWhenRecoveringEndpoints")
  void shouldServeAnAvailableWhenRecoveringEndpointWhileTheTenantIsDegraded(
      final HttpMethod method, final String path) throws Exception {
    // given
    final var mockMvc = mockMvcFor(DatabaseType.ELASTICSEARCH, notReady());

    // when - then
    mockMvc.perform(withBody(method, path)).andExpect(request().asyncStarted());
  }

  @ParameterizedTest
  @MethodSource("availableWhenRecoveringEndpoints")
  void shouldStillRejectAnAvailableWhenRecoveringEndpointOnAStorageThatCannotServeIt(
      final HttpMethod method, final String path) throws Exception {
    // given
    final var mockMvc = mockMvcFor(DatabaseType.RDBMS, notReady());

    // when - then
    mockMvc.perform(withBody(method, path)).andExpect(status().isForbidden());
  }

  private static MockHttpServletRequestBuilder withBody(
      final HttpMethod method, final String path) {
    return request(method, path)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"backupId\": 1}");
  }

  private static SecondaryStorageReadiness notReady() {
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.isReady(any())).thenReturn(false);
    return readiness;
  }

  private static MockMvc mockMvcFor(final DatabaseType secondaryStorageType) {
    return mockMvcFor(secondaryStorageType, SecondaryStorageReadiness.ALWAYS_READY);
  }

  private static MockMvc mockMvcFor(
      final DatabaseType secondaryStorageType, final SecondaryStorageReadiness readiness) {
    final var historyBackupServices = mock(HistoryBackupServices.class);
    when(historyBackupServices.listBackups(any(), anyBoolean(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    final var serviceRegistry = mock(ServiceRegistry.class);
    when(historyBackupServices.takeBackup(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(historyBackupServices.getBackupState(anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(historyBackupServices.deleteBackup(anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(serviceRegistry.historyBackupServices(any())).thenReturn(historyBackupServices);
    final var authenticationProvider = mock(CamundaAuthenticationProvider.class);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(mock(CamundaAuthentication.class));

    final var interceptor =
        new SecondaryStorageInterceptor(tenantId -> secondaryStorageType, readiness);
    return MockMvcBuilders.standaloneSetup(
            new HistoryBackupController(serviceRegistry, authenticationProvider))
        .addInterceptors(interceptor)
        .setControllerAdvice(new GlobalControllerExceptionHandler())
        .build();
  }
}

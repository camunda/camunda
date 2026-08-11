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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MockMvc;
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

  private static MockMvc mockMvcFor(final DatabaseType secondaryStorageType) {
    final var historyBackupServices = mock(HistoryBackupServices.class);
    when(historyBackupServices.listBackups(any(), anyBoolean(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    final var serviceRegistry = mock(ServiceRegistry.class);
    when(serviceRegistry.historyBackupServices(any())).thenReturn(historyBackupServices);
    final var authenticationProvider = mock(CamundaAuthenticationProvider.class);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(mock(CamundaAuthentication.class));

    final var interceptor =
        new SecondaryStorageInterceptor(
            tenantId -> secondaryStorageType, SecondaryStorageReadiness.ALWAYS_READY);
    return MockMvcBuilders.standaloneSetup(
            new HistoryBackupController(serviceRegistry, authenticationProvider))
        .addInterceptors(interceptor)
        .setControllerAdvice(new GlobalControllerExceptionHandler())
        .build();
  }
}

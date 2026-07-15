/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.RuntimeBackupServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(RuntimeBackupController.class)
public class RuntimeBackupControllerTest extends RestControllerTest {

  private static final String BASE_URL = "/v2/backups/runtime";

  @MockitoBean private RuntimeBackupServices runtimeBackupServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.backupServices(any())).thenReturn(runtimeBackupServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void takeBackupShouldReturnAcceptedWithGeneratedId() {
    // given
    when(runtimeBackupServices.takeBackup(isNull(), any()))
        .thenReturn(CompletableFuture.completedFuture(42L));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.ACCEPTED)
        .expectBody()
        .jsonPath("$.backupId")
        .isEqualTo(42);

    verify(runtimeBackupServices).takeBackup(isNull(), any());
  }

  @Test
  void takeBackupShouldForwardExplicitBackupId() {
    // given
    when(runtimeBackupServices.takeBackup(eq(17L), any()))
        .thenReturn(CompletableFuture.completedFuture(17L));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupId\": 17}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.ACCEPTED);

    verify(runtimeBackupServices).takeBackup(eq(17L), any());
  }

  @Test
  void takeBackupShouldReturnBadRequestOnInvalidArgument() {
    // given
    when(runtimeBackupServices.takeBackup(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("must be > 0", Status.INVALID_ARGUMENT)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void takeBackupShouldReturnConflictOnAlreadyExists() {
    // given
    when(runtimeBackupServices.takeBackup(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("already exists", Status.ALREADY_EXISTS)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void takeBackupShouldReturnForbiddenWhenUnauthorized() {
    // given
    when(runtimeBackupServices.takeBackup(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Unauthorized to perform operation", Status.FORBIDDEN)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void getBackupShouldReturnOk() {
    // given
    final var status = new BackupStatus(42L, State.COMPLETED, Optional.empty(), List.of());
    when(runtimeBackupServices.getBackupStatus(eq(42L), any()))
        .thenReturn(CompletableFuture.completedFuture(status));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "/42")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.backupId")
        .isEqualTo(42)
        .jsonPath("$.state")
        .isEqualTo("COMPLETED");
  }

  @Test
  void getBackupShouldReturnNotFoundWhenAbsent() {
    // given
    when(runtimeBackupServices.getBackupStatus(eq(42L), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("does not exist", Status.NOT_FOUND)));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "/42")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void listBackupsShouldDefaultToEmptyPrefix() {
    // given
    when(runtimeBackupServices.listBackups(isNull(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when - then
    webClient
        .get()
        .uri(BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(runtimeBackupServices).listBackups(isNull(), any());
  }

  @Test
  void listBackupsShouldForwardPrefix() {
    // given
    when(runtimeBackupServices.listBackups(eq("12*"), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "?prefix=12*")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(runtimeBackupServices).listBackups(eq("12*"), any());
  }

  @Test
  void deleteBackupShouldReturnNoContent() {
    // given
    when(runtimeBackupServices.deleteBackup(eq(42L), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .delete()
        .uri(BASE_URL + "/42")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(runtimeBackupServices).deleteBackup(eq(42L), any());
  }

  @Test
  void getRuntimeBackupStateShouldReturnOk() {
    // given
    final var state =
        new RuntimeBackupServices.RuntimeBackupState(
            new CheckpointStateResponse(), new BackupRangesResponse());
    when(runtimeBackupServices.getRuntimeState(any()))
        .thenReturn(CompletableFuture.completedFuture(state));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "/state")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void getRuntimeBackupStateShouldReturnServiceUnavailableOnIncompleteTopology() {
    // given
    when(runtimeBackupServices.getRuntimeState(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("Topology is incomplete", Status.UNAVAILABLE)));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "/state")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void syncRuntimeBackupStateShouldReturnOk() {
    // given
    final var state =
        new RuntimeBackupServices.RuntimeBackupState(
            new CheckpointStateResponse(), new BackupRangesResponse());
    when(runtimeBackupServices.syncRuntimeState(any()))
        .thenReturn(CompletableFuture.completedFuture(state));

    // when - then
    webClient
        .post()
        .uri(BASE_URL + "/state/sync")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void deleteRuntimeBackupStateShouldReturnNoContent() {
    // given
    when(runtimeBackupServices.deleteRuntimeState(any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .delete()
        .uri(BASE_URL + "/state")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(runtimeBackupServices).deleteRuntimeState(any());
  }
}

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.HistoryBackupServices;
import io.camunda.service.backup.HistoryBackupSnapshot;
import io.camunda.service.backup.HistoryBackupState;
import io.camunda.service.backup.HistoryBackupStateCode;
import io.camunda.service.backup.HistoryBackupTaken;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(HistoryBackupController.class)
public class HistoryBackupControllerTest extends RestControllerTest {

  private static final String BASE_URL = "/v2/backups/history";
  private static final OffsetDateTime START_TIME =
      OffsetDateTime.of(2026, 8, 11, 9, 30, 0, 0, ZoneOffset.UTC);

  @MockitoBean private HistoryBackupServices historyBackupServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.historyBackupServices(any())).thenReturn(historyBackupServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void takeBackupShouldReturnAcceptedWithTheScheduledSnapshots() {
    // given
    when(historyBackupServices.takeBackup(eq(17L), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new HistoryBackupTaken(17L, List.of("part-1", "part-2"))));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupId\": 17}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.ACCEPTED)
        .expectBody()
        .jsonPath("$.backupId")
        .isEqualTo(17)
        .jsonPath("$.scheduledSnapshots")
        .isEqualTo(List.of("part-1", "part-2"));

    verify(historyBackupServices).takeBackup(eq(17L), any());
  }

  @Test
  void takeBackupShouldReturnBadRequestWhenBackupIdIsMissing() {
    // given unlike runtime backups, history backups have no generated-id mode
    when(historyBackupServices.takeBackup(isNull(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("A backupId must be provided", Status.INVALID_ARGUMENT)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verify(historyBackupServices).takeBackup(isNull(), any());
  }

  @Test
  void takeBackupShouldReturnConflictOnDuplicateId() {
    // given
    when(historyBackupServices.takeBackup(anyLong(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("already exists", Status.ALREADY_EXISTS)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupId\": 17}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void takeBackupShouldReturnConflictWhenAnotherBackupIsRunning() {
    // given
    when(historyBackupServices.takeBackup(anyLong(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("another backup is running", Status.INVALID_STATE)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupId\": 17}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void takeBackupShouldReturnForbiddenWhenUnauthorized() {
    // given
    when(historyBackupServices.takeBackup(anyLong(), any()))
        .thenReturn(CompletableFuture.failedFuture(new ServiceException("nope", Status.FORBIDDEN)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupId\": 17}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void getBackupShouldReturnTheBackupWithItsSnapshots() {
    // given
    when(historyBackupServices.getBackupState(eq(17L), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new HistoryBackupState(
                    17L,
                    HistoryBackupStateCode.FAILED,
                    "out of disk space",
                    List.of(
                        new HistoryBackupSnapshot(
                            "part-1", "PARTIAL", START_TIME, List.of("shard 0 failed"))))));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "/17")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.backupId")
        .isEqualTo(17)
        .jsonPath("$.state")
        .isEqualTo("FAILED")
        .jsonPath("$.failureReason")
        .isEqualTo("out of disk space")
        .jsonPath("$.details[0].snapshotName")
        .isEqualTo("part-1")
        .jsonPath("$.details[0].state")
        .isEqualTo("PARTIAL")
        .jsonPath("$.details[0].failures[0]")
        .isEqualTo("shard 0 failed");
  }

  @Test
  void getBackupShouldReturnNotFoundForAnUnknownId() {
    // given
    when(historyBackupServices.getBackupState(eq(17L), any()))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("no such id", Status.NOT_FOUND)));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "/17")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void getBackupShouldReturnServiceUnavailableWhenTheStoreIsUnreachable() {
    // given
    when(historyBackupServices.getBackupState(eq(17L), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("unreachable", Status.UNAVAILABLE)));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "/17")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void listBackupsShouldDefaultToVerboseAndNoPrefix() {
    // given
    when(historyBackupServices.listBackups(isNull(), anyBoolean(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when - then
    webClient
        .get()
        .uri(BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(historyBackupServices).listBackups(isNull(), eq(true), any());
  }

  @Test
  void listBackupsShouldForwardPrefixAndVerbose() {
    // given
    when(historyBackupServices.listBackups(eq("17*"), anyBoolean(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(
                    new HistoryBackupState(
                        17L, HistoryBackupStateCode.COMPLETED, null, List.of()))));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "?prefix=17*&verbose=false")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].backupId")
        .isEqualTo(17)
        .jsonPath("$[0].state")
        .isEqualTo("COMPLETED");

    verify(historyBackupServices).listBackups(eq("17*"), eq(false), any());
  }

  @Test
  void listBackupsShouldReturnBadRequestOnInvalidPrefix() {
    // given
    when(historyBackupServices.listBackups(any(), anyBoolean(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("must end with '*'", Status.INVALID_ARGUMENT)));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "?prefix=17")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void deleteBackupShouldReturnNoContent() {
    // given
    when(historyBackupServices.deleteBackup(eq(17L), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .delete()
        .uri(BASE_URL + "/17")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(historyBackupServices).deleteBackup(eq(17L), any());
  }

  @Test
  void deleteBackupShouldReturnNotFoundForAnUnknownId() {
    // given
    when(historyBackupServices.deleteBackup(eq(17L), any()))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("no such id", Status.NOT_FOUND)));

    // when - then
    webClient
        .delete()
        .uri(BASE_URL + "/17")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();
  }
}

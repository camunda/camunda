/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.ClusterHistoryBackupServices;
import io.camunda.service.ClusterHistoryBackupServices.ClusterHistoryBackup;
import io.camunda.service.ClusterHistoryBackupServices.ClusterHistoryBackupTaken;
import io.camunda.service.ClusterHistoryBackupServices.PhysicalTenantBackupState;
import io.camunda.service.ClusterHistoryBackupServices.PhysicalTenantBackupTaken;
import io.camunda.service.ClusterHistoryBackupServices.TenantBackupStateCode;
import io.camunda.service.backup.HistoryBackupSnapshot;
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

@WebMvcTest(ClusterHistoryBackupController.class)
public class ClusterHistoryBackupControllerTest extends RestControllerTest {

  private static final String BASE_URL = "/cluster/v2/backups/history";
  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final OffsetDateTime START_TIME =
      OffsetDateTime.of(2026, 8, 18, 9, 30, 0, 0, ZoneOffset.UTC);

  @MockitoBean private ClusterHistoryBackupServices clusterHistoryBackupServices;
  @MockitoBean private ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.clusterHistoryBackupServices()).thenReturn(clusterHistoryBackupServices);
  }

  @Test
  void takeBackupShouldReturnAcceptedWithTheSnapshotsScheduledPerPhysicalTenant() {
    // given
    when(clusterHistoryBackupServices.takeBackup(isNull(), eq(17L)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterHistoryBackupTaken(
                    17L,
                    List.of(
                        new PhysicalTenantBackupTaken(TENANT_A, List.of("a-1")),
                        new PhysicalTenantBackupTaken(TENANT_B, List.of("b-1"))))));

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
        .jsonPath("$.physicalTenants[0].physicalTenantId")
        .isEqualTo(TENANT_A)
        .jsonPath("$.physicalTenants[0].scheduledSnapshots")
        .isEqualTo(List.of("a-1"))
        .jsonPath("$.physicalTenants[1].physicalTenantId")
        .isEqualTo(TENANT_B);
  }

  @Test
  void takeBackupShouldReturnConflictWhenAPhysicalTenantAlreadyHoldsTheId() {
    // given no backup is scheduled anywhere when one tenant rejects the id
    when(clusterHistoryBackupServices.takeBackup(isNull(), anyLong()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("already exists on 'tenantb'", Status.ALREADY_EXISTS)));

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
  void takeBackupShouldReturnBadRequestWhenBackupIdIsMissing() {
    // given
    when(clusterHistoryBackupServices.takeBackup(isNull(), isNull()))
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
  }

  @Test
  void getBackupShouldReturnOkListingEveryPhysicalTenantIncludingTheOnesHoldingNothing() {
    // given a backup only one physical tenant holds — a supported outcome, not a failure
    when(clusterHistoryBackupServices.getBackup(isNull(), eq(17L)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterHistoryBackup(
                    17L,
                    List.of(
                        new PhysicalTenantBackupState(
                            TENANT_A,
                            TenantBackupStateCode.COMPLETED,
                            null,
                            List.of(
                                new HistoryBackupSnapshot(
                                    "a-1", "SUCCESS", START_TIME, List.of()))),
                        new PhysicalTenantBackupState(
                            TENANT_B, TenantBackupStateCode.NOT_FOUND, null, List.of())))));

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
        .jsonPath("$.physicalTenants[0].state")
        .isEqualTo("COMPLETED")
        .jsonPath("$.physicalTenants[0].details[0].snapshotName")
        .isEqualTo("a-1")
        .jsonPath("$.physicalTenants[1].physicalTenantId")
        .isEqualTo(TENANT_B)
        .jsonPath("$.physicalTenants[1].state")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void getBackupShouldReturnNotFoundWhenNoPhysicalTenantHoldsIt() {
    // given
    when(clusterHistoryBackupServices.getBackup(isNull(), anyLong()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("none of [tenanta, tenantb] holds it", Status.NOT_FOUND)));

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
  void getBackupShouldReturnServiceUnavailableWhenAPhysicalTenantCannotBeObserved() {
    // given the fan-out is all-or-nothing, so a partly observable cluster is not a partial success
    when(clusterHistoryBackupServices.getBackup(isNull(), anyLong()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("'tenantb': connection refused", Status.UNAVAILABLE)));

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
  void listBackupsShouldReturnOkWithTheBackupsGroupedByBackupId() {
    // given
    when(clusterHistoryBackupServices.listBackups(isNull(), isNull(), eq(true)))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(
                    new ClusterHistoryBackup(
                        17L,
                        List.of(
                            new PhysicalTenantBackupState(
                                TENANT_A, TenantBackupStateCode.COMPLETED, null, List.of()))))));

    // when - then
    webClient
        .get()
        .uri(BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].backupId")
        .isEqualTo(17)
        .jsonPath("$[0].physicalTenants[0].physicalTenantId")
        .isEqualTo(TENANT_A);
  }

  @Test
  void listBackupsShouldPassPrefixAndVerboseThrough() {
    // given
    when(clusterHistoryBackupServices.listBackups(isNull(), eq("17*"), eq(false)))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "?prefix=17*&verbose=false")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterHistoryBackupServices).listBackups(isNull(), eq("17*"), eq(false));
  }

  @Test
  void deleteBackupShouldReturnNoContentWhenOnlyOnePhysicalTenantHeldIt() {
    // given
    when(clusterHistoryBackupServices.deleteBackup(isNull(), eq(17L)))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .delete()
        .uri(BASE_URL + "/17")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void deleteBackupShouldReturnNotFoundWhenNoPhysicalTenantHeldIt() {
    // given
    when(clusterHistoryBackupServices.deleteBackup(isNull(), anyLong()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("none of [tenanta, tenantb] holds it", Status.NOT_FOUND)));

    // when - then
    webClient
        .delete()
        .uri(BASE_URL + "/17")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void shouldNarrowToThePhysicalTenantNamedByTheQueryParameter() {
    // given
    when(clusterHistoryBackupServices.getBackup(eq(TENANT_A), eq(17L)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterHistoryBackup(
                    17L,
                    List.of(
                        new PhysicalTenantBackupState(
                            TENANT_A, TenantBackupStateCode.COMPLETED, null, List.of())))));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "/17?physicalTenantId=" + TENANT_A)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterHistoryBackupServices).getBackup(TENANT_A, 17L);
  }

  /** A blank id names no tenant, so it is cluster-wide rather than a rejected request. */
  @Test
  void shouldTreatABlankPhysicalTenantIdAsClusterWide() {
    // given
    when(clusterHistoryBackupServices.listBackups(isNull(), isNull(), anyBoolean()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "?physicalTenantId=")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterHistoryBackupServices).listBackups(isNull(), isNull(), anyBoolean());
  }

  @Test
  void shouldReturnNotFoundForAnUnknownPhysicalTenantId() {
    // given
    when(clusterHistoryBackupServices.listBackups(eq("nosuchtenant"), isNull(), anyBoolean()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("this cluster only has [tenanta]", Status.NOT_FOUND)));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "?physicalTenantId=nosuchtenant")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();
  }
}

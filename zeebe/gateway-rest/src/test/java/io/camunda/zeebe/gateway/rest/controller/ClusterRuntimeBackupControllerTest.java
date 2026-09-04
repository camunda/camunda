/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.ClusterRuntimeBackupServices;
import io.camunda.service.ClusterRuntimeBackupServices.ClusterRuntimeBackup;
import io.camunda.service.ClusterRuntimeBackupServices.ClusterRuntimeBackupTaken;
import io.camunda.service.ClusterRuntimeBackupServices.PhysicalTenantRuntimeBackup;
import io.camunda.service.ClusterRuntimeBackupServices.PhysicalTenantRuntimeBackupTaken;
import io.camunda.service.ClusterRuntimeBackupServices.TakeOutcome;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.PartitionBackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ClusterRuntimeBackupController.class)
public class ClusterRuntimeBackupControllerTest extends RestControllerTest {

  private static final String BASE_URL = "/cluster/v2/backups/runtime";
  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  @MockitoBean private ClusterRuntimeBackupServices clusterRuntimeBackupServices;
  @MockitoBean private ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.clusterRuntimeBackupServices()).thenReturn(clusterRuntimeBackupServices);
  }

  @Test
  void takeBackupShouldReturnAcceptedWithThePerPhysicalTenantOutcome() {
    // given
    when(clusterRuntimeBackupServices.takeBackup(isNull(), eq(17L)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterRuntimeBackupTaken(
                    List.of(triggered(TENANT_A, 17L), triggered(TENANT_B, 17L)), null)));

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
        .jsonPath("$.physicalTenants[0].physicalTenantId")
        .isEqualTo(TENANT_A)
        .jsonPath("$.physicalTenants[0].backupId")
        .isEqualTo(17)
        .jsonPath("$.physicalTenants[0].outcome")
        .isEqualTo("TRIGGERED")
        .jsonPath("$.physicalTenants[1].physicalTenantId")
        .isEqualTo(TENANT_B);
  }

  @Test
  void takeBackupShouldTriggerEveryTenantsOwnIdWhenNoBodyIsSent() {
    // given a cluster that generates its ids, so each tenant answers with its own
    when(clusterRuntimeBackupServices.takeBackup(isNull(), isNull()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterRuntimeBackupTaken(
                    List.of(triggered(TENANT_A, 101L), triggered(TENANT_B, 202L)), null)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.ACCEPTED)
        .expectBody()
        .jsonPath("$.physicalTenants[0].backupId")
        .isEqualTo(101)
        .jsonPath("$.physicalTenants[1].backupId")
        .isEqualTo(202);

    verify(clusterRuntimeBackupServices).takeBackup(isNull(), isNull());
  }

  /**
   * The point of ADR 003 D4: the error status says the request failed, and the body says which
   * backups are nevertheless running — without it an operator cannot find them, let alone delete
   * them.
   */
  @Test
  void takeBackupShouldReportTheTriggeredTenantsAlongsideAnErrorStatus() {
    // given
    when(clusterRuntimeBackupServices.takeBackup(isNull(), eq(17L)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterRuntimeBackupTaken(
                    List.of(
                        triggered(TENANT_A, 17L),
                        new PhysicalTenantRuntimeBackupTaken(
                            TENANT_B, TakeOutcome.FAILED, null, "already exists")),
                    Status.ALREADY_EXISTS)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupId\": 17}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.physicalTenants[0].outcome")
        .isEqualTo("TRIGGERED")
        .jsonPath("$.physicalTenants[0].backupId")
        .isEqualTo(17)
        .jsonPath("$.physicalTenants[1].outcome")
        .isEqualTo("FAILED")
        .jsonPath("$.physicalTenants[1].reason")
        .isEqualTo("already exists");
  }

  @Test
  void takeBackupShouldReturnServiceUnavailableWhenATenantCouldNotBeReached() {
    // given
    when(clusterRuntimeBackupServices.takeBackup(isNull(), eq(17L)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterRuntimeBackupTaken(
                    List.of(
                        triggered(TENANT_A, 17L),
                        new PhysicalTenantRuntimeBackupTaken(
                            TENANT_B, TakeOutcome.FAILED, null, "connection refused")),
                    Status.UNAVAILABLE)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupId\": 17}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .jsonPath("$.physicalTenants[0].outcome")
        .isEqualTo("TRIGGERED");
  }

  /**
   * A tenant whose broker connection was cut may or may not be running a backup, so it is reported
   * as `UNKNOWN` with the id to check it under — not as failed, which would claim there is nothing
   * to clean up.
   */
  @Test
  void takeBackupShouldReportAnIndeterminateTenantAsUnknownWithTheIdToCheck() {
    // given
    when(clusterRuntimeBackupServices.takeBackup(isNull(), eq(17L)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterRuntimeBackupTaken(
                    List.of(
                        triggered(TENANT_A, 17L),
                        new PhysicalTenantRuntimeBackupTaken(
                            TENANT_B, TakeOutcome.UNKNOWN, 17L, "the connection was cut")),
                    Status.ABORTED)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupId\": 17}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.BAD_GATEWAY)
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.physicalTenants[1].outcome")
        .isEqualTo("UNKNOWN")
        .jsonPath("$.physicalTenants[1].backupId")
        .isEqualTo(17)
        .jsonPath("$.physicalTenants[1].reason")
        .isEqualTo("the connection was cut");
  }

  /**
   * A rejection that predates the fan-out answers with a problem detail, not the cluster body:
   * there is nothing running for the body to report, and that difference is what tells a caller
   * whether it has backups to clean up.
   */
  @Test
  void takeBackupShouldReturnProblemDetailWhenTheBackupIdModesAreMixed() {
    // given
    when(clusterRuntimeBackupServices.takeBackup(isNull(), eq(17L)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException(
                    "the physical tenants [tenantb] generate their own ids",
                    Status.INVALID_ARGUMENT)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupId\": 17}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("the physical tenants [tenantb] generate their own ids");
  }

  @Test
  void getBackupShouldReturnOkWithTheAggregatedStateAndEveryPhysicalTenant() {
    // given a backup only one physical tenant holds — a supported outcome, not a failure
    when(clusterRuntimeBackupServices.getBackup(isNull(), eq(17L)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterRuntimeBackup(
                    17L,
                    State.INCOMPLETE,
                    null,
                    List.of(
                        backupOn(TENANT_A, 17L, State.COMPLETED),
                        backupOn(TENANT_B, 17L, State.DOES_NOT_EXIST)))));

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
        .isEqualTo("INCOMPLETE")
        .jsonPath("$.physicalTenants[0].state")
        .isEqualTo("COMPLETED")
        .jsonPath("$.physicalTenants[0].details[0].partitionId")
        .isEqualTo(1)
        .jsonPath("$.physicalTenants[1].physicalTenantId")
        .isEqualTo(TENANT_B)
        .jsonPath("$.physicalTenants[1].state")
        .isEqualTo("DOES_NOT_EXIST");
  }

  @Test
  void getBackupShouldReturnNotFoundWhenNoPhysicalTenantHoldsIt() {
    // given
    when(clusterRuntimeBackupServices.getBackup(isNull(), anyLong()))
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
  void listBackupsShouldReturnOkWithTheBackupsGroupedByBackupId() {
    // given
    when(clusterRuntimeBackupServices.listBackups(isNull(), isNull(), isNull(), isNull()))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(
                    new ClusterRuntimeBackup(
                        17L,
                        State.COMPLETED,
                        null,
                        List.of(backupOn(TENANT_A, 17L, State.COMPLETED))))));

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
        .jsonPath("$[0].state")
        .isEqualTo("COMPLETED")
        .jsonPath("$[0].physicalTenants[0].physicalTenantId")
        .isEqualTo(TENANT_A);
  }

  @Test
  void listBackupsShouldPassThePrefixThrough() {
    // given
    when(clusterRuntimeBackupServices.listBackups(isNull(), eq("17*"), isNull(), isNull()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "?prefix=17*")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterRuntimeBackupServices).listBackups(isNull(), eq("17*"), isNull(), isNull());
  }

  @Test
  void deleteBackupShouldReturnNoContent() {
    // given
    when(clusterRuntimeBackupServices.deleteBackup(isNull(), eq(17L)))
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
  void deleteRuntimeStateShouldReturnNoContent() {
    // given
    when(clusterRuntimeBackupServices.deleteRuntimeState(isNull()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when - then
    webClient
        .delete()
        .uri(BASE_URL + "/state")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(clusterRuntimeBackupServices).deleteRuntimeState(isNull());
  }

  /** The literal {@code /state} path must not be read as a {@code {backupId}}. */
  @Test
  void getRuntimeStateShouldNotBeRoutedToTheBackupIdHandler() {
    // given
    when(clusterRuntimeBackupServices.getRuntimeState(isNull()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("'tenantb': connection refused", Status.UNAVAILABLE)));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "/state")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

    verify(clusterRuntimeBackupServices).getRuntimeState(isNull());
    verify(clusterRuntimeBackupServices, never()).getBackup(any(), anyLong());
  }

  @Test
  void syncRuntimeStateShouldPassTheNarrowedTenantThrough() {
    // given
    when(clusterRuntimeBackupServices.syncRuntimeState(eq(TENANT_A)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("'tenanta': connection refused", Status.UNAVAILABLE)));

    // when - then
    webClient
        .post()
        .uri(BASE_URL + "/state/sync?physicalTenantId=" + TENANT_A)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

    verify(clusterRuntimeBackupServices).syncRuntimeState(TENANT_A);
  }

  @Test
  void listBackupsShouldPassTheCursorAndLimitThrough() {
    // given
    when(clusterRuntimeBackupServices.listBackups(isNull(), isNull(), eq(170L), eq(50)))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "?before=170&limit=50")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterRuntimeBackupServices).listBackups(isNull(), isNull(), eq(170L), eq(50));
  }

  @Test
  void shouldNarrowToThePhysicalTenantNamedByTheQueryParameter() {
    // given
    when(clusterRuntimeBackupServices.getBackup(eq(TENANT_A), eq(17L)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterRuntimeBackup(
                    17L,
                    State.COMPLETED,
                    null,
                    List.of(backupOn(TENANT_A, 17L, State.COMPLETED)))));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "/17?physicalTenantId=" + TENANT_A)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterRuntimeBackupServices).getBackup(TENANT_A, 17L);
  }

  /** A blank id names no tenant, so it is cluster-wide rather than a rejected request. */
  @Test
  void shouldTreatABlankPhysicalTenantIdAsClusterWide() {
    // given
    when(clusterRuntimeBackupServices.listBackups(isNull(), isNull(), isNull(), isNull()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when - then
    webClient
        .get()
        .uri(BASE_URL + "?physicalTenantId=")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterRuntimeBackupServices).listBackups(isNull(), isNull(), isNull(), isNull());
  }

  @Test
  void shouldReturnNotFoundForAnUnknownPhysicalTenantId() {
    // given
    when(clusterRuntimeBackupServices.listBackups(eq("nosuchtenant"), isNull(), isNull(), isNull()))
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

  private static PhysicalTenantRuntimeBackupTaken triggered(
      final String physicalTenantId, final long backupId) {
    return new PhysicalTenantRuntimeBackupTaken(
        physicalTenantId, TakeOutcome.TRIGGERED, backupId, null);
  }

  private static PhysicalTenantRuntimeBackup backupOn(
      final String physicalTenantId, final long backupId, final State state) {
    return new PhysicalTenantRuntimeBackup(
        physicalTenantId,
        new BackupStatus(
            backupId,
            state,
            Optional.empty(),
            List.of(
                new PartitionBackupStatus(
                    1,
                    state == State.COMPLETED
                        ? BackupStatusCode.COMPLETED
                        : BackupStatusCode.DOES_NOT_EXIST,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    OptionalInt.empty(),
                    Optional.empty()))));
  }
}

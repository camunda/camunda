/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.ClusterHistoryBackupServices.ClusterHistoryBackup;
import io.camunda.service.ClusterHistoryBackupServices.PhysicalTenantBackupState;
import io.camunda.service.ClusterHistoryBackupServices.PhysicalTenantBackupTaken;
import io.camunda.service.ClusterHistoryBackupServices.TenantBackupStateCode;
import io.camunda.service.backup.HistoryBackupApi;
import io.camunda.service.backup.HistoryBackupState;
import io.camunda.service.backup.HistoryBackupStateCode;
import io.camunda.service.backup.HistoryBackupTaken;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The fan-out rules of the cluster-wide history backup endpoints: absence is a successful
 * observation, and anything else is all-or-nothing.
 */
public class ClusterHistoryBackupServicesTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  /**
   * Generous on purpose. The first assertion that reaches a failure path pays the class loading of
   * {@code ErrorMapper}'s dependency graph — broker client, document API, search — on a worker
   * thread, which costs seconds on a cold JVM. A bound near that cost makes the test flaky without
   * saying anything about the code under test, which has no timing behaviour of its own.
   */
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final HistoryBackupApi api = mock(HistoryBackupApi.class);

  private ExecutorService executor;
  private ClusterHistoryBackupServices services;

  @BeforeEach
  public void before() {
    executor = Executors.newFixedThreadPool(2);
    // A bare executor, not `new ApiServicesExecutorProvider(executor)`: the provider wraps it in
    // the physical-tenant propagating decorator, whose spring-web dependency is not on this
    // module's test classpath. Propagation is not what this test is about.
    final var executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(executor);
    // Deliberately unsorted, so the sorted response order is a property of the service.
    services = new ClusterHistoryBackupServices(api, List.of(TENANT_B, TENANT_A), executorProvider);
  }

  @AfterEach
  public void after() {
    executor.shutdownNow();
  }

  // -- take --

  @Test
  public void shouldTakeTheBackupOnEveryPhysicalTenantInTenantIdOrder() {
    // given
    absentOn(TENANT_A, 42L);
    absentOn(TENANT_B, 42L);
    when(api.takeBackup(TENANT_A, 42L)).thenReturn(new HistoryBackupTaken(42L, List.of("a-1")));
    when(api.takeBackup(TENANT_B, 42L)).thenReturn(new HistoryBackupTaken(42L, List.of("b-1")));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then
    assertThat(taken)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              assertThat(result.backupId()).isEqualTo(42L);
              assertThat(result.physicalTenants())
                  .containsExactly(
                      new PhysicalTenantBackupTaken(TENANT_A, List.of("a-1")),
                      new PhysicalTenantBackupTaken(TENANT_B, List.of("b-1")));
            });
  }

  @Test
  public void shouldNotScheduleAnythingWhenOnePhysicalTenantAlreadyHoldsTheBackupId() {
    // given a backup id one tenant already holds — the request is all-or-nothing
    absentOn(TENANT_A, 42L);
    when(api.getBackupState(TENANT_B, 42L)).thenReturn(completedBackup(42L));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then
    assertThat(failureOf(taken))
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo(Status.ALREADY_EXISTS);
              assertThat(e.getMessage()).contains(TENANT_B);
            });
    verify(api, never()).takeBackup(anyString(), anyLong());
  }

  @Test
  public void shouldNotScheduleAnythingWhenOnePhysicalTenantCannotBeReached() {
    // given
    absentOn(TENANT_A, 42L);
    when(api.getBackupState(TENANT_B, 42L))
        .thenThrow(new ServiceException("connection refused", Status.UNAVAILABLE));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then
    assertThat(failureOf(taken).getStatus()).isEqualTo(Status.UNAVAILABLE);
    verify(api, never()).takeBackup(anyString(), anyLong());
  }

  /**
   * A repository absent from the store must never be mistaken for a tenant that simply holds no
   * backup — that is what would let a cluster-wide take start on a tenant that cannot serve it.
   */
  @Test
  public void shouldNotTreatAMissingRepositoryAsAFreeBackupId() {
    // given
    absentOn(TENANT_A, 42L);
    when(api.getBackupState(TENANT_B, 42L))
        .thenThrow(new ServiceException("no repository", Status.FORBIDDEN));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then
    assertThat(failureOf(taken).getStatus()).isEqualTo(Status.FORBIDDEN);
    verify(api, never()).takeBackup(anyString(), anyLong());
  }

  @Test
  public void shouldRejectAnAbsentBackupId() {
    // when
    final var taken = services.takeBackup(null, null);

    // then
    assertThat(failureOf(taken).getStatus()).isEqualTo(Status.INVALID_ARGUMENT);
    verify(api, never()).getBackupState(anyString(), anyLong());
  }

  // -- get --

  @Test
  public void shouldReportABackupOnlyOnePhysicalTenantHoldsAsSuccess() {
    // given a single-tenant backup — a supported outcome, not a degraded one
    when(api.getBackupState(TENANT_A, 42L)).thenReturn(completedBackup(42L));
    absentOn(TENANT_B, 42L);

    // when
    final var backup = services.getBackup(null, 42L);

    // then every targeted tenant is listed, including the one holding nothing
    assertThat(backup)
        .succeedsWithin(TIMEOUT)
        .isEqualTo(
            new ClusterHistoryBackup(
                42L,
                List.of(
                    new PhysicalTenantBackupState(
                        TENANT_A, TenantBackupStateCode.COMPLETED, null, List.of()),
                    new PhysicalTenantBackupState(
                        TENANT_B, TenantBackupStateCode.NOT_FOUND, null, List.of()))));
  }

  @Test
  public void shouldReportABackupNoPhysicalTenantHoldsAsNotFound() {
    // given
    absentOn(TENANT_A, 42L);
    absentOn(TENANT_B, 42L);

    // when
    final var backup = services.getBackup(null, 42L);

    // then
    assertThat(failureOf(backup).getStatus()).isEqualTo(Status.NOT_FOUND);
  }

  @Test
  public void shouldFailTheReadWhenOnePhysicalTenantCannotBeObserved() {
    // given a tenant that holds the backup and one whose state is unknown
    when(api.getBackupState(TENANT_A, 42L)).thenReturn(completedBackup(42L));
    when(api.getBackupState(TENANT_B, 42L))
        .thenThrow(new ServiceException("connection refused", Status.UNAVAILABLE));

    // when
    final var backup = services.getBackup(null, 42L);

    // then the observable part is not reported as if it were the whole picture
    assertThat(failureOf(backup))
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo(Status.UNAVAILABLE);
              assertThat(e.getMessage()).contains(TENANT_B).contains("connection refused");
            });
  }

  /** Different causes have no honest shared status, so the request answers 500 rather than pick. */
  @Test
  public void shouldFallBackToInternalWhenPhysicalTenantsFailForDifferentReasons() {
    // given
    when(api.getBackupState(TENANT_A, 42L))
        .thenThrow(new ServiceException("connection refused", Status.UNAVAILABLE));
    when(api.getBackupState(TENANT_B, 42L))
        .thenThrow(new ServiceException("no repository", Status.FORBIDDEN));

    // when
    final var backup = services.getBackup(null, 42L);

    // then
    assertThat(failureOf(backup))
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo(Status.INTERNAL);
              assertThat(e.getMessage()).contains(TENANT_A).contains(TENANT_B);
            });
  }

  // -- list --

  @Test
  public void shouldGroupListedBackupsByIdMostRecentFirst() {
    // given
    when(api.getBackups(TENANT_A, true, "*"))
        .thenReturn(List.of(completedBackup(7L), completedBackup(3L)));
    when(api.getBackups(TENANT_B, true, "*")).thenReturn(List.of(completedBackup(7L)));

    // when
    final var backups = services.listBackups(null, null, true);

    // then
    assertThat(backups)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            listed -> {
              assertThat(listed).map(ClusterHistoryBackup::backupId).containsExactly(7L, 3L);
              assertThat(listed.getFirst().physicalTenants())
                  .map(PhysicalTenantBackupState::physicalTenantId)
                  .containsExactly(TENANT_A, TENANT_B);
              // a tenant holding none of an id contributes no entry — it is not an error
              assertThat(listed.getLast().physicalTenants())
                  .map(PhysicalTenantBackupState::physicalTenantId)
                  .containsExactly(TENANT_A);
            });
  }

  @Test
  public void shouldFailTheListingWhenOnePhysicalTenantCannotBeRead() {
    // given
    when(api.getBackups(TENANT_A, true, "*")).thenReturn(List.of(completedBackup(7L)));
    when(api.getBackups(TENANT_B, true, "*"))
        .thenThrow(new ServiceException("connection refused", Status.UNAVAILABLE));

    // when
    final var backups = services.listBackups(null, null, true);

    // then a tenant that cannot be read does not silently drop out of the listing
    assertThat(failureOf(backups).getStatus()).isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  public void shouldRejectAPrefixWithoutAWildcard() {
    // when
    final var backups = services.listBackups(null, "17", true);

    // then
    assertThat(failureOf(backups).getStatus()).isEqualTo(Status.INVALID_ARGUMENT);
    verify(api, never()).getBackups(anyString(), anyBoolean(), anyString());
  }

  // -- delete --

  @Test
  public void shouldDeleteABackupOnlyOnePhysicalTenantHolds() {
    // given a tenant that does not hold it has already reached the requested end state
    doThrow(new ServiceException("no such id", Status.NOT_FOUND))
        .when(api)
        .deleteBackup(TENANT_B, 42L);

    // when
    final var deleted = services.deleteBackup(null, 42L);

    // then
    assertThat(deleted).succeedsWithin(TIMEOUT);
    verify(api).deleteBackup(TENANT_A, 42L);
  }

  @Test
  public void shouldReportADeleteOfABackupNoPhysicalTenantHoldsAsNotFound() {
    // given
    doThrow(new ServiceException("no such id", Status.NOT_FOUND))
        .when(api)
        .deleteBackup(anyString(), anyLong());

    // when
    final var deleted = services.deleteBackup(null, 42L);

    // then
    assertThat(failureOf(deleted).getStatus()).isEqualTo(Status.NOT_FOUND);
  }

  @Test
  public void shouldFailTheDeleteWhenOnePhysicalTenantCannotBeReached() {
    // given
    doThrow(new ServiceException("connection refused", Status.UNAVAILABLE))
        .when(api)
        .deleteBackup(TENANT_B, 42L);

    // when
    final var deleted = services.deleteBackup(null, 42L);

    // then
    assertThat(failureOf(deleted).getStatus()).isEqualTo(Status.UNAVAILABLE);
  }

  // -- narrowing --

  @Test
  public void shouldNarrowTheFanOutToTheNamedPhysicalTenant() {
    // given
    absentOn(TENANT_A, 42L);
    when(api.takeBackup(TENANT_A, 42L)).thenReturn(new HistoryBackupTaken(42L, List.of("a-1")));

    // when
    final var taken = services.takeBackup(TENANT_A, 42L);

    // then the other tenant is neither read nor written
    assertThat(taken).succeedsWithin(TIMEOUT);
    verify(api, never()).getBackupState(TENANT_B, 42L);
    verify(api, never()).takeBackup(TENANT_B, 42L);
  }

  /**
   * With one targeted tenant the fan-out cannot be partial, so its failures reach the caller
   * unchanged — a narrowed request answers exactly what the per-tenant endpoint would.
   */
  @Test
  public void shouldCollapseToThePerTenantStatusWhenNarrowed() {
    // given
    when(api.getBackupState(TENANT_A, 42L))
        .thenThrow(new ServiceException("connection refused", Status.UNAVAILABLE));

    // when
    final var backup = services.getBackup(TENANT_A, 42L);

    // then
    assertThat(failureOf(backup).getStatus()).isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  public void shouldRejectAnUnknownPhysicalTenantBeforeContactingAnyTenant() {
    // when
    final var backup = services.getBackup("nosuchtenant", 42L);

    // then an unknown id is a request error, never a tenant that failed
    assertThat(failureOf(backup))
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo(Status.NOT_FOUND);
              assertThat(e.getMessage()).contains("nosuchtenant");
            });
    verify(api, never()).getBackupState(anyString(), anyLong());
  }

  private void absentOn(final String physicalTenantId, final long backupId) {
    when(api.getBackupState(physicalTenantId, backupId))
        .thenThrow(new ServiceException("no such id", Status.NOT_FOUND));
  }

  private static HistoryBackupState completedBackup(final long backupId) {
    return new HistoryBackupState(backupId, HistoryBackupStateCode.COMPLETED, null, List.of());
  }

  private static ServiceException failureOf(final CompletableFuture<?> future) {
    final var thrown = catchThrowable(() -> future.get(TIMEOUT.toMillis(), MILLISECONDS));
    assertThat(thrown).isNotNull();
    final var cause = thrown.getCause();
    assertThat(cause).isInstanceOf(ServiceException.class);
    return (ServiceException) cause;
  }
}

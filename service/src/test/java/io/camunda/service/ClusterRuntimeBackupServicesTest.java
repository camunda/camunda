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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.ClusterRuntimeBackupServices.PhysicalTenantBackupPort;
import io.camunda.service.ClusterRuntimeBackupServices.TakeOutcome;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.PartitionBackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The contract of the cluster-wide runtime backup endpoints: a partial trigger is reported rather
 * than hidden, absence is a successful observation that folds to {@code INCOMPLETE}, and every
 * other failure is all-or-nothing.
 */
public class ClusterRuntimeBackupServicesTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  /**
   * Generous on purpose. The first assertion that reaches a failure path pays the class loading of
   * {@code ErrorMapper}'s dependency graph on a worker thread, which costs seconds on a cold JVM. A
   * bound near that cost makes the test flaky without saying anything about the code under test,
   * which has no timing behaviour of its own.
   */
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final BackupApi apiA = mock(BackupApi.class);
  private final BackupApi apiB = mock(BackupApi.class);

  private ClusterRuntimeBackupServices services;

  @BeforeEach
  public void before() {
    services = manualOnBothTenants();
  }

  // -- take --

  @Test
  public void shouldTriggerTheBackupOnEveryPhysicalTenantInTenantIdOrder() {
    // given
    when(apiA.takeBackup(TENANT_A, 42L)).thenReturn(CompletableFuture.completedFuture(42L));
    when(apiB.takeBackup(TENANT_B, 42L)).thenReturn(CompletableFuture.completedFuture(42L));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then
    assertThat(taken)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              assertThat(result.failureStatus()).isNull();
              assertThat(result.physicalTenants())
                  .extracting("physicalTenantId", "outcome", "backupId")
                  .containsExactly(
                      tuple(TENANT_A, TakeOutcome.TRIGGERED, 42L),
                      tuple(TENANT_B, TakeOutcome.TRIGGERED, 42L));
            });
  }

  /**
   * The whole reason the trigger reports per-tenant outcomes: an operator who is not told which
   * tenants are running a backup cannot monitor or delete them, and there is nothing that could
   * roll them back.
   */
  @Test
  public void shouldReportTheTriggeredTenantsWhenAnotherTenantFails() {
    // given
    when(apiA.takeBackup(TENANT_A, 42L)).thenReturn(CompletableFuture.completedFuture(42L));
    when(apiB.takeBackup(TENANT_B, 42L))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("already exists", Status.ALREADY_EXISTS)));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then the request fails as a whole, but says what is running
    assertThat(taken)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              assertThat(result.failureStatus()).isEqualTo(Status.ALREADY_EXISTS);
              assertThat(result.physicalTenants())
                  .extracting("physicalTenantId", "outcome", "backupId")
                  .containsExactly(
                      tuple(TENANT_A, TakeOutcome.TRIGGERED, 42L),
                      tuple(TENANT_B, TakeOutcome.FAILED, null));
              assertThat(result.physicalTenants().get(1).reason()).contains("already exists");
            });
  }

  /**
   * A tenant whose topology is incomplete throws before returning a future, and must not abort the
   * fan-out before the healthy tenants are even asked — otherwise one broken tenant makes the
   * cluster-wide trigger useless instead of partially effective.
   */
  @Test
  public void shouldTriggerTheHealthyTenantsWhenAnotherRejectsSynchronously() {
    // given
    when(apiA.takeBackup(TENANT_A, 42L)).thenReturn(CompletableFuture.completedFuture(42L));
    when(apiB.takeBackup(TENANT_B, 42L))
        .thenThrow(new ServiceException("incomplete topology", Status.UNAVAILABLE));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then
    assertThat(taken)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              assertThat(result.failureStatus()).isEqualTo(Status.UNAVAILABLE);
              assertThat(result.physicalTenants())
                  .extracting("physicalTenantId", "outcome")
                  .containsExactly(
                      tuple(TENANT_A, TakeOutcome.TRIGGERED), tuple(TENANT_B, TakeOutcome.FAILED));
            });
    verify(apiA).takeBackup(TENANT_A, 42L);
  }

  @Test
  public void shouldAnswerInternalWhenTheFailedTenantsDisagreeOnAStatus() {
    // given a two-tenant cluster where both fail, for different reasons
    when(apiA.takeBackup(TENANT_A, 42L))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("gone", Status.UNAVAILABLE)));
    when(apiB.takeBackup(TENANT_B, 42L))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("already exists", Status.ALREADY_EXISTS)));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then no single status is honest
    assertThat(taken)
        .succeedsWithin(TIMEOUT)
        .satisfies(result -> assertThat(result.failureStatus()).isEqualTo(Status.INTERNAL));
  }

  @Test
  public void shouldGenerateAnIdPerPhysicalTenantWhenEveryTenantGeneratesIds() {
    // given both tenants take scheduled backups, so each generates its own id
    services = servicesWith(generating(TENANT_A, apiA), generating(TENANT_B, apiB));
    when(apiA.takeBackup(TENANT_A)).thenReturn(CompletableFuture.completedFuture(101L));
    when(apiB.takeBackup(TENANT_B)).thenReturn(CompletableFuture.completedFuture(202L));

    // when
    final var taken = services.takeBackup(null, null);

    // then the response carries an id per tenant, because there is no cluster-wide one
    assertThat(taken)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result ->
                assertThat(result.physicalTenants())
                    .extracting("physicalTenantId", "backupId")
                    .containsExactly(tuple(TENANT_A, 101L), tuple(TENANT_B, 202L)));
  }

  @Test
  public void shouldRejectAnExplicitIdWhenAPhysicalTenantGeneratesIds() {
    // given a cluster mixing the two backup-id modes
    services = servicesWith(manual(TENANT_A, apiA), generating(TENANT_B, apiB));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then the tenant standing in the way is named, and nothing is triggered anywhere
    assertThat(failureOf(taken))
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo(Status.INVALID_ARGUMENT);
              assertThat(e.getMessage()).contains(TENANT_B).doesNotContain(TENANT_A);
            });
    verify(apiA, never()).takeBackup(anyString(), anyLong());
    verify(apiB, never()).takeBackup(anyString(), anyLong());
  }

  @Test
  public void shouldRejectAMissingIdWhenAPhysicalTenantTakesManualBackups() {
    // given a cluster mixing the two backup-id modes
    services = servicesWith(manual(TENANT_A, apiA), generating(TENANT_B, apiB));

    // when
    final var taken = services.takeBackup(null, null);

    // then
    assertThat(failureOf(taken))
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo(Status.INVALID_ARGUMENT);
              assertThat(e.getMessage()).contains(TENANT_A).doesNotContain(TENANT_B);
            });
    verify(apiA, never()).takeBackup(anyString());
    verify(apiB, never()).takeBackup(anyString());
  }

  /**
   * A cluster mixing the modes can still be driven one tenant at a time, which is the escape hatch
   * ADR 003 leaves for it — so narrowing must judge only the named tenant's mode.
   */
  @Test
  public void shouldAcceptAnExplicitIdWhenNarrowedToAManualTenantOfAMixedCluster() {
    // given
    services = servicesWith(manual(TENANT_A, apiA), generating(TENANT_B, apiB));
    when(apiA.takeBackup(TENANT_A, 42L)).thenReturn(CompletableFuture.completedFuture(42L));

    // when
    final var taken = services.takeBackup(TENANT_A, 42L);

    // then
    assertThat(taken)
        .succeedsWithin(TIMEOUT)
        .satisfies(result -> assertThat(result.failureStatus()).isNull());
  }

  /**
   * A cut connection may or may not have been accepted, so calling it {@code FAILED} would tell the
   * operator no backup is running where one might be — the silent partial trigger this response
   * exists to prevent.
   */
  @Test
  public void shouldReportAnIndeterminateTriggerAsUnknownKeepingTheRequestedId() {
    // given
    when(apiA.takeBackup(TENANT_A, 42L)).thenReturn(CompletableFuture.completedFuture(42L));
    when(apiB.takeBackup(TENANT_B, 42L))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("the connection was cut prematurely", Status.ABORTED)));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then the tenant is neither triggered nor failed, and carries the id to check it under
    assertThat(taken)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              assertThat(result.failureStatus()).isEqualTo(Status.ABORTED);
              assertThat(result.physicalTenants())
                  .extracting("physicalTenantId", "outcome", "backupId")
                  .containsExactly(
                      tuple(TENANT_A, TakeOutcome.TRIGGERED, 42L),
                      tuple(TENANT_B, TakeOutcome.UNKNOWN, 42L));
            });
  }

  @Test
  public void shouldReportATimedOutTriggerAsUnknown() {
    // given a gateway-to-broker timeout, which is equally indeterminate
    when(apiA.takeBackup(TENANT_A, 42L)).thenReturn(CompletableFuture.completedFuture(42L));
    when(apiB.takeBackup(TENANT_B, 42L))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("request timed out", Status.DEADLINE_EXCEEDED)));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then
    assertThat(taken)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result ->
                assertThat(result.physicalTenants())
                    .extracting("physicalTenantId", "outcome")
                    .containsExactly(
                        tuple(TENANT_A, TakeOutcome.TRIGGERED),
                        tuple(TENANT_B, TakeOutcome.UNKNOWN)));
  }

  /**
   * A definite failure must not carry an id: reporting one would send the operator looking for a
   * backup that is certainly not there.
   */
  @Test
  public void shouldReportNoIdForADefiniteFailure() {
    // given
    when(apiA.takeBackup(TENANT_A, 42L)).thenReturn(CompletableFuture.completedFuture(42L));
    when(apiB.takeBackup(TENANT_B, 42L))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("already exists", Status.ALREADY_EXISTS)));

    // when
    final var taken = services.takeBackup(null, 42L);

    // then
    assertThat(taken)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result ->
                assertThat(result.physicalTenants())
                    .extracting("physicalTenantId", "outcome", "backupId")
                    .containsExactly(
                        tuple(TENANT_A, TakeOutcome.TRIGGERED, 42L),
                        tuple(TENANT_B, TakeOutcome.FAILED, null)));
  }

  // -- get --

  @Test
  public void shouldFoldABackupOnlyOnePhysicalTenantHoldsToIncomplete() {
    // given a backup one tenant holds and the other does not — a supported outcome, not a failure
    when(apiA.getStatus(TENANT_A, 42L))
        .thenReturn(CompletableFuture.completedFuture(backup(42L, State.COMPLETED)));
    when(apiB.getStatus(TENANT_B, 42L))
        .thenReturn(CompletableFuture.completedFuture(backup(42L, State.DOES_NOT_EXIST)));

    // when
    final var backup = services.getBackup(null, 42L);

    // then every targeted tenant is reported, and the cluster-wide state says it is not usable
    assertThat(backup)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              assertThat(result.state()).isEqualTo(State.INCOMPLETE);
              assertThat(result.physicalTenants())
                  .extracting("physicalTenantId")
                  .containsExactly(TENANT_A, TENANT_B);
            });
  }

  @Test
  public void shouldFoldABackupEveryPhysicalTenantCompletedToCompleted() {
    // given
    when(apiA.getStatus(TENANT_A, 42L))
        .thenReturn(CompletableFuture.completedFuture(backup(42L, State.COMPLETED)));
    when(apiB.getStatus(TENANT_B, 42L))
        .thenReturn(CompletableFuture.completedFuture(backup(42L, State.COMPLETED)));

    // when
    final var backup = services.getBackup(null, 42L);

    // then
    assertThat(backup)
        .succeedsWithin(TIMEOUT)
        .satisfies(result -> assertThat(result.state()).isEqualTo(State.COMPLETED));
  }

  @Test
  public void shouldFoldToFailedAndNameTheFailedPhysicalTenant() {
    // given
    when(apiA.getStatus(TENANT_A, 42L))
        .thenReturn(CompletableFuture.completedFuture(backup(42L, State.COMPLETED)));
    when(apiB.getStatus(TENANT_B, 42L))
        .thenReturn(
            CompletableFuture.completedFuture(
                failedBackup(42L, "the store rejected the snapshot")));

    // when
    final var backup = services.getBackup(null, 42L);

    // then
    assertThat(backup)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              assertThat(result.state()).isEqualTo(State.FAILED);
              assertThat(result.failureReason())
                  .contains(TENANT_B)
                  .contains("the store rejected the snapshot");
            });
  }

  @Test
  public void shouldReportNotFoundWhenNoPhysicalTenantHoldsTheBackup() {
    // given
    when(apiA.getStatus(TENANT_A, 42L))
        .thenReturn(CompletableFuture.completedFuture(backup(42L, State.DOES_NOT_EXIST)));
    when(apiB.getStatus(TENANT_B, 42L))
        .thenReturn(CompletableFuture.completedFuture(backup(42L, State.DOES_NOT_EXIST)));

    // when
    final var backup = services.getBackup(null, 42L);

    // then
    assertThat(failureOf(backup).getStatus()).isEqualTo(Status.NOT_FOUND);
  }

  @Test
  public void shouldFailTheWholeReadWhenAPhysicalTenantCannotBeObserved() {
    // given
    when(apiA.getStatus(TENANT_A, 42L))
        .thenReturn(CompletableFuture.completedFuture(backup(42L, State.COMPLETED)));
    when(apiB.getStatus(TENANT_B, 42L))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("connection refused", Status.UNAVAILABLE)));

    // when
    final var backup = services.getBackup(null, 42L);

    // then a partly observable cluster is not a partial success
    assertThat(failureOf(backup))
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo(Status.UNAVAILABLE);
              assertThat(e.getMessage()).contains(TENANT_B);
            });
  }

  // -- list --

  @Test
  public void shouldGroupTheListingByBackupIdMostRecentFirst() {
    // given tenantA holds two backups and tenantB only the older one
    when(apiA.listBackups(TENANT_A, "*", OptionalLong.empty(), OptionalInt.empty()))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(backup(43L, State.IN_PROGRESS), backup(42L, State.COMPLETED))));
    when(apiB.listBackups(TENANT_B, "*", OptionalLong.empty(), OptionalInt.empty()))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup(42L, State.COMPLETED))));

    // when
    final var backups = services.listBackups(null, null);

    // then every group reports every targeted tenant, so the id only tenantA holds is INCOMPLETE
    assertThat(backups)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              assertThat(result).extracting("backupId").containsExactly(43L, 42L);
              assertThat(result.get(0).state()).isEqualTo(State.INCOMPLETE);
              assertThat(result.get(0).physicalTenants())
                  .extracting("physicalTenantId", "backup.status")
                  .containsExactly(
                      tuple(TENANT_A, State.IN_PROGRESS), tuple(TENANT_B, State.DOES_NOT_EXIST));
              assertThat(result.get(1).state()).isEqualTo(State.COMPLETED);
              assertThat(result.get(1).physicalTenants())
                  .extracting("physicalTenantId")
                  .containsExactly(TENANT_A, TENANT_B);
            });
  }

  /**
   * The state of a listed group has to mean what it means on a single-id read, or an operator
   * scanning the listing for the backups the cluster can be restored from cannot trust it.
   */
  @Test
  public void shouldFoldAListedGroupTheSameWayASingleIdReadDoes() {
    // given a backup only one tenant holds, and both a listing and a direct read of it
    when(apiA.listBackups(TENANT_A, "*", OptionalLong.empty(), OptionalInt.empty()))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup(42L, State.COMPLETED))));
    when(apiB.listBackups(TENANT_B, "*", OptionalLong.empty(), OptionalInt.empty()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(apiA.getStatus(TENANT_A, 42L))
        .thenReturn(CompletableFuture.completedFuture(backup(42L, State.COMPLETED)));
    when(apiB.getStatus(TENANT_B, 42L))
        .thenReturn(CompletableFuture.completedFuture(backup(42L, State.DOES_NOT_EXIST)));

    // when
    final var listed = services.listBackups(null, null);
    final var read = services.getBackup(null, 42L);

    // then both say the same thing about the same backup
    assertThat(listed)
        .succeedsWithin(TIMEOUT)
        .satisfies(result -> assertThat(result.get(0).state()).isEqualTo(State.INCOMPLETE));
    assertThat(read)
        .succeedsWithin(TIMEOUT)
        .satisfies(result -> assertThat(result.state()).isEqualTo(State.INCOMPLETE));
  }

  /**
   * A listing asks each tenant for the backups it has, so a tenant that holds nothing for a listed
   * id has no partition detail to report — unlike the single-id read, where the broker answers per
   * partition.
   */
  @Test
  public void shouldReportNoPartitionDetailForATenantHoldingNothingInAListing() {
    // given
    when(apiA.listBackups(TENANT_A, "*", OptionalLong.empty(), OptionalInt.empty()))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup(42L, State.COMPLETED))));
    when(apiB.listBackups(TENANT_B, "*", OptionalLong.empty(), OptionalInt.empty()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    final var backups = services.listBackups(null, null);

    // then
    assertThat(backups)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              final var absent = result.get(0).physicalTenants().get(1);
              assertThat(absent.physicalTenantId()).isEqualTo(TENANT_B);
              assertThat(absent.backup().backupId()).isEqualTo(42L);
              assertThat(absent.backup().partitions()).isEmpty();
            });
  }

  @Test
  public void shouldReportOnlyBackupsEveryTenantWasEnumeratedPast() {
    // given tenantA pages 100, 90, 80 while tenantB pages 100, 95, 85
    when(apiA.listBackups(TENANT_A, "*", OptionalLong.empty(), OptionalInt.of(3)))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(
                    backup(100L, State.COMPLETED),
                    backup(90L, State.COMPLETED),
                    backup(80L, State.COMPLETED))));
    when(apiB.listBackups(TENANT_B, "*", OptionalLong.empty(), OptionalInt.of(3)))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(
                    backup(100L, State.COMPLETED),
                    backup(95L, State.COMPLETED),
                    backup(85L, State.COMPLETED))));

    // when
    final var backups = services.listBackups(null, null, null, 3);

    // then 85 and 80 wait for the next page, because tenantA may still hold statuses below 80
    assertThat(backups)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> assertThat(result).extracting("backupId").containsExactly(100L, 95L, 90L));
  }

  @Test
  public void shouldRejectAPageOutsideTheContract() {
    // when
    final var zeroLimit = services.listBackups(null, null, null, 0);
    final var negativeCursor = services.listBackups(null, null, -1L, null);

    // then
    assertThat(failureOf(zeroLimit).getStatus()).isEqualTo(Status.INVALID_ARGUMENT);
    assertThat(failureOf(negativeCursor).getStatus()).isEqualTo(Status.INVALID_ARGUMENT);
    verify(apiA, never()).listBackups(anyString(), anyString(), any(), any());
  }

  /**
   * The published {@code BackupIdPrefix} is digits plus one wildcard, and backup ids are numbers,
   * so anything else can never match. Rejecting it says the request was malformed, where passing it
   * to the store returns an empty list that reads as "no such backups".
   */
  @ParameterizedTest
  @ValueSource(strings = {"17", "abc*", "1**", "*17", "17*x", "*-suffix", "1 *"})
  public void shouldRejectAPrefixTheContractDoesNotAllow(final String prefix) {
    // when
    final var backups = services.listBackups(null, prefix);

    // then
    assertThat(failureOf(backups).getStatus()).isEqualTo(Status.INVALID_ARGUMENT);
    verify(apiA, never()).listBackups(anyString(), anyString(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"*", "17*"})
  public void shouldAcceptAPrefixTheContractAllows(final String prefix) {
    // given
    when(apiA.listBackups(TENANT_A, prefix, OptionalLong.empty(), OptionalInt.empty()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(apiB.listBackups(TENANT_B, prefix, OptionalLong.empty(), OptionalInt.empty()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when - then
    assertThat(services.listBackups(null, prefix)).succeedsWithin(TIMEOUT);
  }

  // -- delete --

  @Test
  public void shouldDeleteFromEveryPhysicalTenant() {
    // given
    when(apiA.deleteBackup(TENANT_A, 42L)).thenReturn(CompletableFuture.completedFuture(null));
    when(apiB.deleteBackup(TENANT_B, 42L)).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var deleted = services.deleteBackup(null, 42L);

    // then
    assertThat(deleted).succeedsWithin(TIMEOUT);
    verify(apiA).deleteBackup(TENANT_A, 42L);
    verify(apiB).deleteBackup(TENANT_B, 42L);
  }

  @Test
  public void shouldFailTheWholeDeleteWhenAPhysicalTenantCannotBeReached() {
    // given
    when(apiA.deleteBackup(TENANT_A, 42L)).thenReturn(CompletableFuture.completedFuture(null));
    when(apiB.deleteBackup(TENANT_B, 42L))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("connection refused", Status.UNAVAILABLE)));

    // when
    final var deleted = services.deleteBackup(null, 42L);

    // then
    assertThat(failureOf(deleted).getStatus()).isEqualTo(Status.UNAVAILABLE);
  }

  // -- state --

  @Test
  public void shouldReportTheRuntimeStateOfEveryPhysicalTenantSeparately() {
    // given
    final var checkpointsA = mock(CheckpointStateResponse.class);
    final var checkpointsB = mock(CheckpointStateResponse.class);
    final var ranges = mock(BackupRangesResponse.class);
    when(apiA.getCheckpointState(TENANT_A))
        .thenReturn(CompletableFuture.completedFuture(checkpointsA));
    when(apiB.getCheckpointState(TENANT_B))
        .thenReturn(CompletableFuture.completedFuture(checkpointsB));
    when(apiA.getBackupRanges(TENANT_A)).thenReturn(CompletableFuture.completedFuture(ranges));
    when(apiB.getBackupRanges(TENANT_B)).thenReturn(CompletableFuture.completedFuture(ranges));

    // when
    final var states = services.getRuntimeState(null);

    // then nothing is folded: checkpoint ids only mean anything within one tenant's partitions
    assertThat(states)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result ->
                assertThat(result.physicalTenants())
                    .extracting("physicalTenantId")
                    .containsExactly(TENANT_A, TENANT_B));
  }

  @Test
  public void shouldFailTheWholeStateReadWhenOneSubRequestFails() {
    // given a tenant whose ranges cannot be read, though its checkpoint state can
    when(apiA.getCheckpointState(TENANT_A))
        .thenReturn(CompletableFuture.completedFuture(mock(CheckpointStateResponse.class)));
    when(apiB.getCheckpointState(TENANT_B))
        .thenReturn(CompletableFuture.completedFuture(mock(CheckpointStateResponse.class)));
    when(apiA.getBackupRanges(TENANT_A))
        .thenReturn(CompletableFuture.completedFuture(mock(BackupRangesResponse.class)));
    when(apiB.getBackupRanges(TENANT_B))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("gone", Status.UNAVAILABLE)));

    // when
    final var states = services.getRuntimeState(null);

    // then a half-read tenant would be indistinguishable from one with nothing to report
    assertThat(failureOf(states))
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo(Status.UNAVAILABLE);
              assertThat(e.getMessage()).contains(TENANT_B);
            });
  }

  @Test
  public void shouldSyncTheRuntimeStateOfEveryPhysicalTenant() {
    // given
    when(apiA.syncMetadata(TENANT_A))
        .thenReturn(CompletableFuture.completedFuture(mock(BackupRangesResponse.class)));
    when(apiB.syncMetadata(TENANT_B))
        .thenReturn(CompletableFuture.completedFuture(mock(BackupRangesResponse.class)));
    when(apiA.getCheckpointState(TENANT_A))
        .thenReturn(CompletableFuture.completedFuture(mock(CheckpointStateResponse.class)));
    when(apiB.getCheckpointState(TENANT_B))
        .thenReturn(CompletableFuture.completedFuture(mock(CheckpointStateResponse.class)));

    // when
    final var states = services.syncRuntimeState(null);

    // then
    assertThat(states).succeedsWithin(TIMEOUT);
    verify(apiA).syncMetadata(TENANT_A);
    verify(apiB).syncMetadata(TENANT_B);
  }

  @Test
  public void shouldDeleteTheRuntimeStateOfEveryPhysicalTenant() {
    // given
    when(apiA.deleteRuntimeState(TENANT_A)).thenReturn(CompletableFuture.completedFuture(null));
    when(apiB.deleteRuntimeState(TENANT_B)).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var deleted = services.deleteRuntimeState(null);

    // then
    assertThat(deleted).succeedsWithin(TIMEOUT);
    verify(apiA).deleteRuntimeState(TENANT_A);
    verify(apiB).deleteRuntimeState(TENANT_B);
  }

  // -- narrowing --

  @Test
  public void shouldNarrowTheFanOutToTheNamedPhysicalTenant() {
    // given
    when(apiA.deleteBackup(TENANT_A, 42L)).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var deleted = services.deleteBackup(TENANT_A, 42L);

    // then the other tenant is never contacted
    assertThat(deleted).succeedsWithin(TIMEOUT);
    verify(apiB, never()).deleteBackup(anyString(), anyLong());
  }

  /**
   * The {@code BackupId} schema allows only positive ids, so an id outside it is a request error.
   * Let through, it reads back as a 404 — indistinguishable from an id that is merely absent, when
   * the request could never have been served at all.
   */
  @ParameterizedTest
  @ValueSource(longs = {0L, -1L})
  public void shouldRejectANonPositiveBackupIdOnEveryOperation(final long backupId) {
    // when - then
    assertThat(failureOf(services.getBackup(null, backupId)).getStatus())
        .isEqualTo(Status.INVALID_ARGUMENT);
    assertThat(failureOf(services.deleteBackup(null, backupId)).getStatus())
        .isEqualTo(Status.INVALID_ARGUMENT);
    assertThat(failureOf(services.takeBackup(null, backupId)).getStatus())
        .isEqualTo(Status.INVALID_ARGUMENT);

    // and no tenant is contacted
    verify(apiA, never()).getStatus(anyString(), anyLong());
    verify(apiA, never()).deleteBackup(anyString(), anyLong());
    verify(apiA, never()).takeBackup(anyString(), anyLong());
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
    verify(apiA, never()).getStatus(anyString(), anyLong());
    verify(apiB, never()).getStatus(anyString(), anyLong());
  }

  private ClusterRuntimeBackupServices manualOnBothTenants() {
    return servicesWith(manual(TENANT_A, apiA), manual(TENANT_B, apiB));
  }

  private static ClusterRuntimeBackupServices servicesWith(
      final PhysicalTenantBackupPort a, final PhysicalTenantBackupPort b) {
    // Deliberately unsorted, so the sorted response order is a property of the service.
    return new ClusterRuntimeBackupServices(List.of(b, a));
  }

  private static PhysicalTenantBackupPort manual(
      final String physicalTenantId, final BackupApi api) {
    return new PhysicalTenantBackupPort(physicalTenantId, api, false);
  }

  private static PhysicalTenantBackupPort generating(
      final String physicalTenantId, final BackupApi api) {
    return new PhysicalTenantBackupPort(physicalTenantId, api, true);
  }

  private static BackupStatus backup(final long backupId, final State state) {
    return new BackupStatus(backupId, state, Optional.empty(), List.of(partition(state, null)));
  }

  private static BackupStatus failedBackup(final long backupId, final String reason) {
    return new BackupStatus(
        backupId, State.FAILED, Optional.of(reason), List.of(partition(State.FAILED, reason)));
  }

  private static PartitionBackupStatus partition(final State state, final String failureReason) {
    return new PartitionBackupStatus(
        1,
        state == State.INCOMPLETE
            ? BackupStatusCode.DOES_NOT_EXIST
            : BackupStatusCode.valueOf(state.name()),
        Optional.ofNullable(failureReason),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalInt.empty(),
        Optional.empty());
  }

  private static ServiceException failureOf(final CompletableFuture<?> future) {
    final var thrown = catchThrowable(() -> future.get(TIMEOUT.toMillis(), MILLISECONDS));
    assertThat(thrown).isNotNull();
    final var cause = thrown.getCause();
    assertThat(cause).isInstanceOf(ServiceException.class);
    return (ServiceException) cause;
  }
}

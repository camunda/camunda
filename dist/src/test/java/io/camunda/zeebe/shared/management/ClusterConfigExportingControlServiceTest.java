/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExportingStateChangeOperation;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class ClusterConfigExportingControlServiceTest {

  private final ClusterConfigurationManagementRequestSender sender =
      mock(ClusterConfigurationManagementRequestSender.class);
  private final ClusterConfigExportingControlService service =
      new ClusterConfigExportingControlService(
          sender, Duration.ofMillis(1), Duration.ofSeconds(10));

  @Test
  void shouldMapResumeToExporting() {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(sender.changeExporterState(captor.capture())).thenReturn(emptyPlan());

    // when
    service.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID).join();

    // then
    assertThat(captor.getValue().state()).isEqualTo(ExportingState.EXPORTING);
  }

  @Test
  void shouldMapPauseToPaused() {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(sender.changeExporterState(captor.capture())).thenReturn(emptyPlan());

    // when
    service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID).join();

    // then
    assertThat(captor.getValue().state()).isEqualTo(ExportingState.PAUSED);
  }

  @Test
  void shouldMapSoftPauseToSoftPaused() {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(sender.changeExporterState(captor.capture())).thenReturn(emptyPlan());

    // when
    service.softPauseExporting(DEFAULT_PHYSICAL_TENANT_ID).join();

    // then
    assertThat(captor.getValue().state()).isEqualTo(ExportingState.SOFT_PAUSED);
  }

  @Test
  void shouldCompleteImmediatelyWhenPlanIsEmpty() {
    // given
    when(sender.changeExporterState(any())).thenReturn(emptyPlan());

    // when - then
    assertThat(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldCompleteWhenChangeCompletes() {
    // given
    when(sender.changeExporterState(any())).thenReturn(plan(7));
    when(sender.getTopology())
        .thenReturn(topology(Optional.of(completed(7, Status.COMPLETED)), Optional.empty()));

    // when - then
    assertThat(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldPollUntilChangeCompletes() {
    // given
    when(sender.changeExporterState(any())).thenReturn(plan(7));
    when(sender.getTopology())
        .thenReturn(topology(Optional.empty(), Optional.of(pending(7))))
        .thenReturn(topology(Optional.empty(), Optional.of(pending(7))))
        .thenReturn(topology(Optional.of(completed(7, Status.COMPLETED)), Optional.empty()));

    // when
    service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID).join();

    // then
    verify(sender, atLeast(3)).getTopology();
  }

  @Test
  void shouldCompleteWhenSupersededByNewerChange() {
    // given a newer change already completed -> our change finished before it
    when(sender.changeExporterState(any())).thenReturn(plan(7));
    when(sender.getTopology())
        .thenReturn(topology(Optional.of(completed(8, Status.COMPLETED)), Optional.empty()));

    // when - then
    assertThat(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldFailWhenChangeFailed() {
    // given
    when(sender.changeExporterState(any())).thenReturn(plan(7));
    when(sender.getTopology())
        .thenReturn(topology(Optional.of(completed(7, Status.FAILED)), Optional.empty()));

    // when - then
    assertThatThrownBy(() -> service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID).join())
        .isInstanceOf(CompletionException.class);
  }

  @Test
  void shouldFailWhenSubmitFails() {
    // given
    when(sender.changeExporterState(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.INVALID_REQUEST, "nope"))));

    // when - then
    assertThatThrownBy(() -> service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID).join())
        .isInstanceOf(CompletionException.class);
  }

  @Test
  void shouldFailWhenChangeNeverCompletes() {
    // given
    final var timingOutService =
        new ClusterConfigExportingControlService(
            sender, Duration.ofMillis(1), Duration.ofMillis(5));
    when(sender.changeExporterState(any())).thenReturn(plan(7));
    when(sender.getTopology()).thenReturn(topology(Optional.empty(), Optional.of(pending(7))));

    // when - then
    assertThatThrownBy(() -> timingOutService.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID).join())
        .isInstanceOf(CompletionException.class);
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> emptyPlan() {
    return CompletableFuture.completedFuture(
        Either.right(new ClusterConfigurationChangeResponse(0, Map.of(), Map.of(), List.of())));
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> plan(
      final long changeId) {
    return CompletableFuture.completedFuture(
        Either.right(
            new ClusterConfigurationChangeResponse(
                changeId,
                Map.of(),
                Map.of(),
                List.of(
                    new ExportingStateChangeOperation(
                        MemberId.from("0"), ExportingState.PAUSED)))));
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfiguration>> topology(
      final Optional<CompletedChange> lastChange,
      final Optional<ClusterChangePlan> pendingChanges) {
    return CompletableFuture.completedFuture(
        Either.right(
            new ClusterConfiguration(
                1,
                Map.of(),
                lastChange,
                pendingChanges,
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty())));
  }

  private ClusterChangePlan pending(final long changeId) {
    return ClusterChangePlan.init(changeId, List.of());
  }

  private CompletedChange completed(final long changeId, final Status status) {
    return new CompletedChange(changeId, status, Instant.now(), Instant.now());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.gateway.admin.ExportingRequestBroadcaster;
import io.camunda.zeebe.gateway.admin.ExportingStatus;
import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class ClusterExportingServicesTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String TENANT_C = "tenantc";
  private static final Set<String> THREE_TENANTS = Set.of(TENANT_A, TENANT_B, TENANT_C);

  @Test
  void shouldReportSinglePhaseWhenEveryTenantAgrees() {
    // given
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    THREE_TENANTS.forEach(
        tenantId ->
            when(broadcaster.getExportingStatus(tenantId))
                .thenReturn(CompletableFuture.completedFuture(ExportingStatus.PAUSED)));
    final var services = new ClusterExportingServices(broadcaster, THREE_TENANTS);

    // when
    final var status = services.getExportingStatus().join();

    // then
    assertThat(status).isEqualTo(ExportingStatus.PAUSED);
  }

  @Test
  void shouldFoldToMixedWhenTenantsDisagree() {
    // given
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    when(broadcaster.getExportingStatus(TENANT_A))
        .thenReturn(CompletableFuture.completedFuture(ExportingStatus.PAUSED));
    when(broadcaster.getExportingStatus(TENANT_B))
        .thenReturn(CompletableFuture.completedFuture(ExportingStatus.SOFT_PAUSED));
    final var services = new ClusterExportingServices(broadcaster, Set.of(TENANT_A, TENANT_B));

    // when
    final var status = services.getExportingStatus().join();

    // then
    assertThat(status).isEqualTo(ExportingStatus.MIXED);
  }

  @Test
  void shouldFoldToMixedWhenOneTenantItselfReportsMixed() {
    // given
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    when(broadcaster.getExportingStatus(TENANT_A))
        .thenReturn(CompletableFuture.completedFuture(ExportingStatus.PAUSED));
    when(broadcaster.getExportingStatus(TENANT_B))
        .thenReturn(CompletableFuture.completedFuture(ExportingStatus.MIXED));
    final var services = new ClusterExportingServices(broadcaster, Set.of(TENANT_A, TENANT_B));

    // when
    final var status = services.getExportingStatus().join();

    // then
    assertThat(status).isEqualTo(ExportingStatus.MIXED);
  }

  @Test
  void shouldFoldToMixedWhenEveryTenantReportsMixed() {
    // given — the exact case that breaks a naive ExportingStatus.aggregate() reuse
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    THREE_TENANTS.forEach(
        tenantId ->
            when(broadcaster.getExportingStatus(tenantId))
                .thenReturn(CompletableFuture.completedFuture(ExportingStatus.MIXED)));
    final var services = new ClusterExportingServices(broadcaster, THREE_TENANTS);

    // when
    final var status = services.getExportingStatus().join();

    // then
    assertThat(status).isEqualTo(ExportingStatus.MIXED);
  }

  @Test
  void shouldReturnMixedForEmptyTenantSetViaExplicitGuard() {
    // given
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    final var services = new ClusterExportingServices(broadcaster, Set.of());

    // when
    final var status = services.getExportingStatus().join();

    // then
    assertThat(status).isEqualTo(ExportingStatus.MIXED);
  }

  @Test
  void shouldFailWithServiceExceptionWhenATenantFutureCompletesExceptionally() {
    // given
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    when(broadcaster.getExportingStatus(TENANT_A))
        .thenReturn(CompletableFuture.completedFuture(ExportingStatus.PAUSED));
    when(broadcaster.getExportingStatus(TENANT_B))
        .thenReturn(
            CompletableFuture.failedFuture(new IncompleteTopologyException("no topology yet")));
    final var services = new ClusterExportingServices(broadcaster, Set.of(TENANT_A, TENANT_B));

    // when
    final var future = services.getExportingStatus();

    // then
    assertThat(future)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .withCauseInstanceOf(ServiceException.class)
        .satisfies(
            e ->
                assertThat(((ServiceException) e.getCause()).getStatus())
                    .isEqualTo(Status.UNAVAILABLE));
  }

  @Test
  void shouldReturnServiceExceptionWithoutThrowingWhenBroadcasterThrowsSynchronously() {
    // given — validateTopology() throws before returning a future; must not escape as a raw throw
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    when(broadcaster.getExportingStatus(TENANT_A))
        .thenThrow(new IncompleteTopologyException("no topology yet"));
    final var services = new ClusterExportingServices(broadcaster, Set.of(TENANT_A));

    // when
    final var future = services.getExportingStatus();

    // then
    assertThat(future)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .withCauseInstanceOf(ServiceException.class)
        .satisfies(
            e ->
                assertThat(((ServiceException) e.getCause()).getStatus())
                    .isEqualTo(Status.UNAVAILABLE));
  }

  @Test
  void shouldMapCompletionExceptionWrappedIncompleteTopologyExceptionTo503() {
    // given
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    when(broadcaster.getExportingStatus(TENANT_A))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CompletionException(new IncompleteTopologyException("no topology yet"))));
    final var services = new ClusterExportingServices(broadcaster, Set.of(TENANT_A));

    // when
    final var future = services.getExportingStatus();

    // then
    assertThat(future)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .withCauseInstanceOf(ServiceException.class)
        .satisfies(
            e ->
                assertThat(((ServiceException) e.getCause()).getStatus())
                    .isEqualTo(Status.UNAVAILABLE));
  }

  @Test
  void shouldMapUnknownPhaseFailureToInvalidArgumentNotMixed() {
    // given
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    when(broadcaster.getExportingStatus(TENANT_A))
        .thenReturn(
            CompletableFuture.failedFuture(
                new IllegalArgumentException(
                    "Expected a broker replica to report a known exporter phase, but got 'UNKNOWN'")));
    final var services = new ClusterExportingServices(broadcaster, Set.of(TENANT_A));

    // when
    final var future = services.getExportingStatus();

    // then
    assertThat(future)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .withCauseInstanceOf(ServiceException.class)
        .satisfies(
            e ->
                assertThat(((ServiceException) e.getCause()).getStatus())
                    .isEqualTo(Status.INVALID_ARGUMENT));
  }

  @Test
  void shouldSoftPauseEveryTenantWhenSoftIsTrue() {
    // given
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    THREE_TENANTS.forEach(
        tenantId ->
            when(broadcaster.softPauseExporting(tenantId))
                .thenReturn(CompletableFuture.completedFuture(null)));
    final var services = new ClusterExportingServices(broadcaster, THREE_TENANTS);

    // when
    services.pauseExporting(true).join();

    // then
    THREE_TENANTS.forEach(tenantId -> verify(broadcaster).softPauseExporting(eq(tenantId)));
  }

  @Test
  void shouldHardPauseEveryTenantWhenSoftIsFalse() {
    // given
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    THREE_TENANTS.forEach(
        tenantId ->
            when(broadcaster.pauseExporting(tenantId))
                .thenReturn(CompletableFuture.completedFuture(null)));
    final var services = new ClusterExportingServices(broadcaster, THREE_TENANTS);

    // when
    services.pauseExporting(false).join();

    // then
    THREE_TENANTS.forEach(tenantId -> verify(broadcaster).pauseExporting(eq(tenantId)));
  }

  @Test
  void shouldIncludeFailingTenantIdWhenBroadcasterThrowsSynchronously() {
    // given — validateTopology() throws before returning a future, mirroring the real
    // ExportingRequestBroadcaster; this is the only path IncompleteTopologyException ever takes.
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    when(broadcaster.pauseExporting(TENANT_A)).thenReturn(CompletableFuture.completedFuture(null));
    when(broadcaster.pauseExporting(TENANT_B))
        .thenThrow(new IncompleteTopologyException("no topology yet"));
    final var services = new ClusterExportingServices(broadcaster, Set.of(TENANT_A, TENANT_B));

    // when
    final var future = services.pauseExporting(false);

    // then
    assertThat(future)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .withCauseInstanceOf(ServiceException.class)
        .satisfies(
            e -> {
              final var mapped = (ServiceException) e.getCause();
              assertThat(mapped.getMessage()).contains(TENANT_B).contains("no topology yet");
              assertThat(mapped.getStatus()).isEqualTo(Status.UNAVAILABLE);
            });
  }

  @Test
  void shouldIncludeFailingTenantIdWhenTenantFutureFailsAsynchronously() {
    // given — CompletableFuture.allOf wraps a dependency's failure in a CompletionException
    // (per its own javadoc), which is what a real broker request failure looks like by the time
    // it reaches fanOutOne. A bare instanceof check against the raw throwable would never match
    // this, silently dropping the tenant id for every genuine async failure.
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    when(broadcaster.pauseExporting(TENANT_A)).thenReturn(CompletableFuture.completedFuture(null));
    when(broadcaster.pauseExporting(TENANT_B))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CompletionException(new IncompleteTopologyException("no topology yet"))));
    final var services = new ClusterExportingServices(broadcaster, Set.of(TENANT_A, TENANT_B));

    // when
    final var future = services.pauseExporting(false);

    // then
    assertThat(future)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .withCauseInstanceOf(ServiceException.class)
        .satisfies(
            e -> {
              final var mapped = (ServiceException) e.getCause();
              assertThat(mapped.getMessage()).contains(TENANT_B).contains("no topology yet");
              assertThat(mapped.getStatus()).isEqualTo(Status.UNAVAILABLE);
            });
  }

  @Test
  void shouldResumeEveryTenant() {
    // given
    final var broadcaster = mock(ExportingRequestBroadcaster.class);
    THREE_TENANTS.forEach(
        tenantId ->
            when(broadcaster.resumeExporting(tenantId))
                .thenReturn(CompletableFuture.completedFuture(null)));
    final var services = new ClusterExportingServices(broadcaster, THREE_TENANTS);

    // when
    services.resumeExporting().join();

    // then
    THREE_TENANTS.forEach(tenantId -> verify(broadcaster).resumeExporting(eq(tenantId)));
  }
}

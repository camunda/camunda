/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.dynamic.config.api.ExportingStateController.ClusterWide;
import io.camunda.zeebe.dynamic.config.api.ExportingStatus;
import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/**
 * {@link ClusterExportingServices} is a thin wrapper around {@link ClusterWide} that only adds
 * {@code ServiceException} error mapping (ADR 003 D2); the per-tenant fan-out and status
 * aggregation it used to do itself now live in {@code DynamicConfigExportingStateController} and
 * are covered there.
 */
class ClusterExportingServicesTest {

  @Test
  void shouldDelegateStatusQuery() {
    // given
    final var controller = mock(ClusterWide.class);
    when(controller.getExportingStatus())
        .thenReturn(CompletableFuture.completedFuture(ExportingStatus.PAUSED));
    final var services = new ClusterExportingServices(controller);

    // when
    final var status = services.getExportingStatus().join();

    // then
    assertThat(status).isEqualTo(ExportingStatus.PAUSED);
  }

  @Test
  void shouldSoftPauseWhenSoftIsTrue() {
    // given
    final var controller = mock(ClusterWide.class);
    when(controller.softPauseExporting()).thenReturn(CompletableFuture.completedFuture(null));
    final var services = new ClusterExportingServices(controller);

    // when
    services.pauseExporting(true).join();

    // then
    verify(controller).softPauseExporting();
  }

  @Test
  void shouldHardPauseWhenSoftIsFalse() {
    // given
    final var controller = mock(ClusterWide.class);
    when(controller.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));
    final var services = new ClusterExportingServices(controller);

    // when
    services.pauseExporting(false).join();

    // then
    verify(controller).pauseExporting();
  }

  @Test
  void shouldResume() {
    // given
    final var controller = mock(ClusterWide.class);
    when(controller.resumeExporting()).thenReturn(CompletableFuture.completedFuture(null));
    final var services = new ClusterExportingServices(controller);

    // when
    services.resumeExporting().join();

    // then
    verify(controller).resumeExporting();
  }

  @Test
  void shouldReturnServiceExceptionWithoutThrowingWhenControllerThrowsSynchronously() {
    // given — a synchronous throw before a future is even returned must not escape as a raw throw
    final var controller = mock(ClusterWide.class);
    when(controller.getExportingStatus())
        .thenThrow(new IncompleteTopologyException("no topology yet"));
    final var services = new ClusterExportingServices(controller);

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
  void shouldMapAsynchronousFailureToServiceException() {
    // given
    final var controller = mock(ClusterWide.class);
    when(controller.getExportingStatus())
        .thenReturn(
            CompletableFuture.failedFuture(new IncompleteTopologyException("no topology yet")));
    final var services = new ClusterExportingServices(controller);

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
  void shouldMapCompletionExceptionWrappedFailureToServiceException() {
    // given — CompletableFuture chains wrap a dependency's failure in a CompletionException; the
    // mapper must still unwrap it to the correct status
    final var controller = mock(ClusterWide.class);
    when(controller.getExportingStatus())
        .thenReturn(
            CompletableFuture.failedFuture(
                new CompletionException(new IncompleteTopologyException("no topology yet"))));
    final var services = new ClusterExportingServices(controller);

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
}

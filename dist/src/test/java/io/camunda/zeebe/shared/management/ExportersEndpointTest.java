/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class ExportersEndpointTest {

  @Test
  void shouldDisableExporterInEveryPhysicalTenantWhenParameterIsAbsent() {
    // given
    final var sender = senderAcceptingExporterChanges();
    final var endpoint = new ExportersEndpoint(sender);

    // when
    endpoint.disableExporter("exporter-1", false, null).join();

    // then
    verify(sender)
        .disableExporter(new ExporterDisableRequest("exporter-1", Optional.empty(), false));
  }

  @Test
  void shouldDisableExporterOnlyInTheGivenPhysicalTenant() {
    // given
    final var sender = senderAcceptingExporterChanges();
    final var endpoint = new ExportersEndpoint(sender);

    // when
    endpoint.disableExporter("exporter-1", true, "tenant-a").join();

    // then
    verify(sender)
        .disableExporter(new ExporterDisableRequest("exporter-1", Optional.of("tenant-a"), true));
  }

  @Test
  void shouldEnableExporterInEveryPhysicalTenantWhenParameterIsAbsent() {
    // given
    final var sender = senderAcceptingExporterChanges();
    final var endpoint = new ExportersEndpoint(sender);

    // when
    endpoint.enableExporter("exporter-1", null, false, null).join();

    // then
    verify(sender)
        .enableExporter(
            new ExporterEnableRequest("exporter-1", Optional.empty(), Optional.empty(), false));
  }

  @Test
  void shouldEnableExporterOnlyInTheGivenPhysicalTenant() {
    // given
    final var sender = senderAcceptingExporterChanges();
    final var endpoint = new ExportersEndpoint(sender);

    // when
    endpoint.enableExporter("exporter-1", null, false, "tenant-a").join();

    // then
    verify(sender)
        .enableExporter(
            new ExporterEnableRequest(
                "exporter-1", Optional.empty(), Optional.of("tenant-a"), false));
  }

  @Test
  void shouldDeleteExporterInEveryPhysicalTenantWhenParameterIsAbsent() {
    // given
    final var sender = senderAcceptingExporterChanges();
    final var endpoint = new ExportersEndpoint(sender);

    // when
    endpoint.deleteExporter("exporter-1", false, null).join();

    // then
    verify(sender).deleteExporter(new ExporterDeleteRequest("exporter-1", Optional.empty(), false));
  }

  @Test
  void shouldDeleteExporterOnlyInTheGivenPhysicalTenant() {
    // given
    final var sender = senderAcceptingExporterChanges();
    final var endpoint = new ExportersEndpoint(sender);

    // when
    endpoint.deleteExporter("exporter-1", true, "tenant-a").join();

    // then
    verify(sender)
        .deleteExporter(new ExporterDeleteRequest("exporter-1", Optional.of("tenant-a"), true));
  }

  @Test
  void shouldTreatABlankPhysicalTenantAsAbsent() {
    // given
    final var sender = senderAcceptingExporterChanges();
    final var endpoint = new ExportersEndpoint(sender);

    // when
    endpoint.disableExporter("exporter-1", false, "  ").join();

    // then
    verify(sender)
        .disableExporter(new ExporterDisableRequest("exporter-1", Optional.empty(), false));
  }

  private static ClusterConfigurationManagementRequestSender senderAcceptingExporterChanges() {
    final var sender = mock(ClusterConfigurationManagementRequestSender.class);
    when(sender.disableExporter(any())).thenReturn(acceptedChange());
    when(sender.enableExporter(any())).thenReturn(acceptedChange());
    when(sender.deleteExporter(any())).thenReturn(acceptedChange());
    return sender;
  }

  private static CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
      acceptedChange() {
    return CompletableFuture.completedFuture(
        Either.right(
            new ClusterConfigurationChangeResponse(
                1L, new LegacyConfigurationChangeResponse(Map.of(), Map.of(), List.of()), null)));
  }
}

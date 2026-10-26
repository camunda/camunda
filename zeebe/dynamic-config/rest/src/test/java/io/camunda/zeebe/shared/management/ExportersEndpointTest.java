/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.management.cluster.Error;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

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

  @Test
  void shouldListExportersOfEveryPhysicalTenant() {
    // given
    final var endpoint =
        new ExportersEndpoint(
            senderReportingTopology(
                configWithExporters(Map.of("tenant-a", "exporter-1", "tenant-b", "exporter-2"))));

    // when
    final var response = endpoint.listExporters(null).join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(exporters(response))
        .extracting(ExporterStatus::getPhysicalTenant, ExporterStatus::getExporterId)
        .containsExactly(tuple("tenant-a", "exporter-1"), tuple("tenant-b", "exporter-2"));
  }

  @Test
  void shouldListExportersOfOnlyTheGivenPhysicalTenant() {
    // given
    final var endpoint =
        new ExportersEndpoint(
            senderReportingTopology(
                configWithExporters(Map.of("tenant-a", "exporter-1", "tenant-b", "exporter-2"))));

    // when
    final var response = endpoint.listExporters("tenant-b").join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(exporters(response))
        .extracting(ExporterStatus::getPhysicalTenant, ExporterStatus::getExporterId)
        .containsExactly(tuple("tenant-b", "exporter-2"));
  }

  @Test
  void shouldRejectAnUnknownPhysicalTenant() {
    // given
    final var endpoint =
        new ExportersEndpoint(
            senderReportingTopology(configWithExporters(Map.of("tenant-a", "exporter-1"))));

    // when
    final var response = endpoint.listExporters("tenant-c").join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(response.getBody())
        .asInstanceOf(InstanceOfAssertFactories.type(Error.class))
        .extracting(Error::getMessage)
        .isEqualTo("Physical tenant 'tenant-c' does not exist");
  }

  @SuppressWarnings("unchecked")
  private static List<ExporterStatus> exporters(final ResponseEntity<?> response) {
    return (List<ExporterStatus>) response.getBody();
  }

  private static ClusterConfigurationManagementRequestSender senderReportingTopology(
      final CurrentClusterConfiguration configuration) {
    final var sender = mock(ClusterConfigurationManagementRequestSender.class);
    when(sender.getTopology())
        .thenReturn(CompletableFuture.completedFuture(Either.right(configuration)));
    return sender;
  }

  /** A configuration with one broker holding one enabled exporter per physical tenant. */
  private static CurrentClusterConfiguration configWithExporters(
      final Map<String, String> exporterPerTenant) {
    final var member = MemberId.from("1");
    final var globalConfiguration =
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(member, new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final Map<String, PartitionGroupConfiguration> groups = new HashMap<>();
    exporterPerTenant.forEach(
        (tenant, exporterId) -> {
          final var partitionConfig =
              new DynamicPartitionConfig(
                  new ExportingConfig(
                      ExportingState.EXPORTING,
                      Map.of(exporterId, new ExporterState(0, State.ENABLED, Optional.empty()))));
          groups.put(
              tenant,
              new PartitionGroupConfiguration(
                  1,
                  0,
                  Map.of(
                      member,
                      BrokerPartitionState.initialize(
                          Map.of(1, PartitionState.active(1, partitionConfig)))),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty()));
        });
    return new CurrentClusterConfiguration(
        1, globalConfiguration, groups, PhasedChangeState.empty());
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

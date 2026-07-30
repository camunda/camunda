/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(RecoveryController.class)
public class RecoveryControllerTest extends RestControllerTest {

  @MockitoBean ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender;

  private void stubValidationSuccess() {
    Mockito.when(clusterConfigurationRequestSender.restore(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    new ClusterConfigurationChangeResponse(0L, Map.of(), Map.of(), List.of()))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/mode", "/physical-tenants/default/v2/mode"})
  void shouldChangeClusterModeAndReturnPlannedChanges(final String baseUrl) {
    // given
    final var changeResponse =
        new ClusterConfigurationChangeResponse(
            7L,
            Map.of(),
            Map.of(),
            List.of(new ModeChangeOperation(MemberId.from("0"), Mode.RECOVERING)));
    Mockito.when(
            clusterConfigurationRequestSender.modeChange(
                new ModeChangeRequest("default", Mode.RECOVERING, false)))
        .thenReturn(CompletableFuture.completedFuture(Either.right(changeResponse)));

    final var expectedResponse =
        """
        {
          "changeId": "7",
          "plannedChanges": [
            {
              "operation": "ModeChangeOperation",
              "mode": "RECOVERING"
            }
          ]
        }
        """;

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "?mode=RECOVERING&dryRun=false")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/mode", "/physical-tenants/default/v2/mode"})
  void shouldMapErrorResponseWhenModeChangeRejected(final String baseUrl) {
    // given
    Mockito.when(
            clusterConfigurationRequestSender.modeChange(
                new ModeChangeRequest("default", Mode.RECOVERING, false)))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(
                    new ErrorResponse(ErrorCode.CONCURRENT_MODIFICATION, "a change is ongoing"))));

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "?mode=RECOVERING&dryRun=false")
        .exchange()
        .expectStatus()
        .is4xxClientError();
  }

  @Test
  void shouldMapInvalidRequestErrorFromCoordinator() {
    // given
    Mockito.when(clusterConfigurationRequestSender.restore(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.INVALID_REQUEST, "bad params"))));

    // when / then
    webClient
        .post()
        .uri("/v2/restore")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100], \"from\": \"2024-01-01T10:00:00Z\"}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldMapInternalErrorFromCoordinator() {
    // given
    Mockito.when(clusterConfigurationRequestSender.restore(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.INTERNAL_ERROR, "boom"))));

    // when / then
    webClient
        .post()
        .uri("/v2/restore")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100]}")
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  @Test
  void shouldMapConcurrentModificationFromCoordinator() {
    // given
    Mockito.when(clusterConfigurationRequestSender.restore(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.CONCURRENT_MODIFICATION, "boom"))));

    // when / then
    webClient
        .post()
        .uri("/v2/restore")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100]}")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void shouldReturnNotFoundWhenNoRestore() {
    // given
    Mockito.when(clusterConfigurationRequestSender.getTopology())
        .thenReturn(CompletableFuture.completedFuture(Either.right(ClusterConfiguration.init())));

    // when / then
    webClient.get().uri("/v2/restore").exchange().expectStatus().isNotFound();
  }

  @Test
  void shouldReturnInProgressRestoreStatus() {
    // given
    final var broker = MemberId.from("1");
    final var startedAt = Instant.parse("2024-01-01T10:00:00Z");
    final var plan =
        new ClusterChangePlan(
            -2L,
            3,
            Status.IN_PROGRESS,
            startedAt,
            List.of(
                new CompletedOperation(
                    new PartitionPreRestoreOperation(broker, 1), startedAt.plusSeconds(1)),
                new CompletedOperation(
                    new PartitionRestoreOperation(broker, 1, new TreeSet<>(List.of(10L, 11L))),
                    startedAt.plusSeconds(2))),
            List.<ClusterConfigurationChangeOperation>of(
                new ModeChangeOperation(broker, Mode.PROCESSING)));
    stubTopology(Optional.empty(), Optional.of(plan));

    // when / then
    webClient
        .get()
        .uri("/v2/restore")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("IN_PROGRESS")
        .jsonPath("$.changeId")
        .isEqualTo("-2")
        .jsonPath("$.startedAt")
        .isEqualTo("2024-01-01T10:00:00Z")
        .jsonPath("$.brokers[0].brokerId")
        .isEqualTo("1")
        .jsonPath("$.brokers[0].partitionsRestored")
        .isEqualTo(1)
        .jsonPath("$.brokers[0].partitionsToRestore")
        .isEqualTo(1)
        .jsonPath("$.brokers[0].partitions[0].partitionId")
        .isEqualTo(1)
        .jsonPath("$.brokers[0].partitions[0].state")
        .isEqualTo("RESTORED")
        .jsonPath("$.brokers[0].partitions[0].backupIds[0]")
        .isEqualTo(10)
        .jsonPath("$.brokers[0].partitions[0].completedAt")
        .isEqualTo("2024-01-01T10:00:02Z");
  }

  @Test
  void shouldReturnNotFoundOnceRestoreHasCompleted() {
    // given
    final var startedAt = Instant.parse("2024-01-01T10:00:00Z");
    final var lastChange =
        new CompletedChange(-2L, Status.COMPLETED, startedAt, startedAt.plusSeconds(300));
    stubTopology(Optional.of(lastChange), Optional.empty());

    // when / then
    webClient.get().uri("/v2/restore").exchange().expectStatus().isNotFound();
  }

  @Test
  void shouldReturnNotFoundForUnrelatedPendingModeTransition() {
    // given
    final var broker = MemberId.from("1");
    final var plan =
        new ClusterChangePlan(
            42L,
            1,
            Status.IN_PROGRESS,
            Instant.parse("2024-01-01T10:00:00Z"),
            List.of(),
            List.<ClusterConfigurationChangeOperation>of(
                new ModeChangeOperation(broker, Mode.PROCESSING)));
    stubTopology(Optional.empty(), Optional.of(plan));

    // when / then
    webClient.get().uri("/v2/restore").exchange().expectStatus().isNotFound();
  }

  private void stubTopology(
      final Optional<CompletedChange> lastChange,
      final Optional<ClusterChangePlan> pendingChanges) {
    final var configuration =
        new ClusterConfiguration(
            2,
            Map.of(),
            lastChange,
            pendingChanges,
            Optional.empty(),
            Optional.empty(),
            0,
            Optional.empty());
    Mockito.when(clusterConfigurationRequestSender.getTopology())
        .thenReturn(CompletableFuture.completedFuture(Either.right(configuration)));
  }

  @Nested
  @TestPropertySource(properties = {"camunda.data.secondary-storage.type=elasticsearch"})
  class Elasticsearch extends SecondaryStorage {

    @Override
    String expectedDatabaseType() {
      return "elasticsearch";
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.data.secondary-storage.type=opensearch"})
  class OpenSearch extends SecondaryStorage {

    @Override
    String expectedDatabaseType() {
      return "opensearch";
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.data.secondary-storage.type=rdbms"})
  class Rdbms extends SecondaryStorage {

    @Override
    String expectedDatabaseType() {
      return "rdbms";
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.data.secondary-storage.type=none"})
  class None extends SecondaryStorage {

    @Override
    String expectedDatabaseType() {
      return "none";
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.primary-storage.backup.continuous=true"
      })
  class RdbmsWithContinuousBackups extends RdbmsSecondaryStorageWithContinuousBackups {

    @Override
    String expectedDatabaseType() {
      return "rdbms";
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=none",
        "camunda.data.primary-storage.backup.continuous=true"
      })
  class NoneWithContinuousBackups extends RdbmsSecondaryStorageWithContinuousBackups {

    @Override
    String expectedDatabaseType() {
      return "none";
    }
  }

  abstract class SecondaryStorage {

    abstract String expectedDatabaseType();

    @ParameterizedTest
    @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
    void shouldForwardBackupIds(final String baseUrl) {
      // given
      stubValidationSuccess();

      // when / then
      webClient
          .post()
          .uri(baseUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{\"backupIds\": [100, 101]}")
          .exchange()
          .expectStatus()
          .isAccepted()
          .expectBody()
          .json("{\"changeId\": \"0\", \"plannedChanges\": []}", JsonCompareMode.STRICT);

      Mockito.verify(clusterConfigurationRequestSender)
          .restore(
              new RestoreRequest(
                  PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                  List.of(100L, 101L),
                  null,
                  null,
                  expectedDatabaseType(),
                  false,
                  false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
    void shouldForwardNoParameters(final String baseUrl) {
      // given
      stubValidationSuccess();

      // when / then
      webClient
          .post()
          .uri(baseUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isAccepted();

      Mockito.verify(clusterConfigurationRequestSender)
          .restore(
              new RestoreRequest(
                  PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                  List.of(),
                  null,
                  null,
                  expectedDatabaseType(),
                  false,
                  false));
    }
  }

  abstract class RdbmsSecondaryStorageWithContinuousBackups {

    abstract String expectedDatabaseType();

    @ParameterizedTest
    @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
    void shouldForwardContinuousFlagWithBackupIds(final String baseUrl) {
      // given
      stubValidationSuccess();

      // when / then
      webClient
          .post()
          .uri(baseUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{\"backupIds\": [100, 101]}")
          .exchange()
          .expectStatus()
          .isAccepted();

      Mockito.verify(clusterConfigurationRequestSender)
          .restore(
              new RestoreRequest(
                  PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                  List.of(100L, 101L),
                  null,
                  null,
                  expectedDatabaseType(),
                  true,
                  false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
    void shouldForwardContinuousFlagWithTimeRange(final String baseUrl) {
      // given
      stubValidationSuccess();

      // when / then
      webClient
          .post()
          .uri(baseUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{\"from\": \"2024-01-01T10:00:00Z\", \"to\": \"2024-01-01T12:00:00Z\"}")
          .exchange()
          .expectStatus()
          .isAccepted();

      Mockito.verify(clusterConfigurationRequestSender)
          .restore(
              new RestoreRequest(
                  PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                  List.of(),
                  "2024-01-01T10:00:00Z",
                  "2024-01-01T12:00:00Z",
                  expectedDatabaseType(),
                  true,
                  false));
    }
  }
}

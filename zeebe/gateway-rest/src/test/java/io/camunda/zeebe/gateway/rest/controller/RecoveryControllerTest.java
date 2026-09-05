/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.service.authorization.Authorizations.BACKUP_RESTORE_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.SYSTEM_UPDATE_AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.gateway.protocol.model.RestoreBrokerStatus;
import io.camunda.gateway.protocol.model.RestorePartitionStatus;
import io.camunda.gateway.protocol.model.RestoreStatusResponse;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.RecoveryServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(RecoveryController.class)
public class RecoveryControllerTest extends RestControllerTest {

  @MockitoBean ServiceRegistry serviceRegistry;
  @MockitoBean RecoveryServices recoveryServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUpRecoveryServices() {
    Mockito.when(serviceRegistry.recoveryServices(Mockito.any())).thenReturn(recoveryServices);
    Mockito.when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  private void stubRestoreAccepted() {
    Mockito.when(recoveryServices.restore(Mockito.any(), Mockito.anyBoolean(), Mockito.any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(changeResponse(0L, List.of()))));
  }

  /** A change plan that transitions the physical tenant this controller is addressed at. */
  private static ClusterConfigurationChangeResponse changeResponse(
      final long changeId, final List<PartitionGroupOperation> plannedChanges) {
    final var phases =
        plannedChanges.isEmpty()
            ? List.<Phase>of()
            : List.<Phase>of(
                PartitionGroupPhase.sequential(
                    Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, plannedChanges)));
    return new ClusterConfigurationChangeResponse(
        changeId,
        new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
            Map.of(), Map.of(), List.<ClusterConfigurationChangeOperation>copyOf(plannedChanges)),
        new ClusterConfigurationChangeResponse.CurrentConfigurationChangeResponse(
            CurrentClusterConfiguration.uninitialized(),
            CurrentClusterConfiguration.uninitialized(),
            phases));
  }

  @Test
  void shouldReturnPlannedChangesOfAGraphPhase() {
    // given — this is the shape a mode change actually plans now: a graph whose awaits wait for
    // every broker's mode change. The response lists the operations; the edges between them are
    // execution detail the API does not report.
    final var memberId = MemberId.from("0");
    final var graph = OperationGraph.builder();
    final var modeChange = graph.add(new ModeChangeOperation(memberId, Mode.RECOVERING));
    graph.add(new AwaitModeChangeOperation(memberId, Mode.RECOVERING), Set.of(modeChange));
    final var changeResponse =
        new ClusterConfigurationChangeResponse(
            7L,
            new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                Map.of(), Map.of(), List.of()),
            new ClusterConfigurationChangeResponse.CurrentConfigurationChangeResponse(
                CurrentClusterConfiguration.uninitialized(),
                CurrentClusterConfiguration.uninitialized(),
                List.of(
                    new PartitionGroupPhase(
                        Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, graph.build())))));
    Mockito.when(
            recoveryServices.changeMode(
                Mockito.eq(Mode.RECOVERING), Mockito.eq(false), Mockito.any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(changeResponse)));

    final var expectedResponse =
        """
        {
          "changeId": "7",
          "plannedChanges": [
            {
              "physicalTenantId": "default",
              "operations": [
                {
                  "operation": "ModeChangeOperation",
                  "mode": "RECOVERING"
                },
                {
                  "operation": "AwaitModeChangeOperation",
                  "mode": "RECOVERING"
                }
              ]
            }
          ]
        }
        """;

    // when / then
    webClient
        .patch()
        .uri("/v2/mode?mode=RECOVERING&dryRun=false")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/mode", "/physical-tenants/default/v2/mode"})
  void shouldChangeClusterModeAndReturnPlannedChanges(final String baseUrl) {
    // given
    final var changeResponse =
        changeResponse(7L, List.of(new ModeChangeOperation(MemberId.from("0"), Mode.RECOVERING)));
    Mockito.when(
            recoveryServices.changeMode(
                Mockito.eq(Mode.RECOVERING), Mockito.eq(false), Mockito.any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(changeResponse)));

    final var expectedResponse =
        """
        {
          "changeId": "7",
          "plannedChanges": [
            {
              "physicalTenantId": "default",
              "operations": [
                {
                  "operation": "ModeChangeOperation",
                  "mode": "RECOVERING"
                }
              ]
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
            recoveryServices.changeMode(
                Mockito.eq(Mode.RECOVERING), Mockito.eq(false), Mockito.any()))
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
  void shouldRejectModeChangeWhenCallerIsNotAuthorized() {
    // given
    Mockito.when(recoveryServices.changeMode(Mockito.any(), Mockito.anyBoolean(), Mockito.any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.createForbiddenException(
                    List.of(BACKUP_RESTORE_AUTHORIZATION, SYSTEM_UPDATE_AUTHORIZATION))));

    // when / then
    webClient
        .patch()
        .uri("/v2/mode?mode=RECOVERING")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 403,
              "title": "FORBIDDEN",
              "detail": "Unauthorized to perform any of the operations: 'RESTORE' on 'BACKUP' or 'UPDATE' on 'SYSTEM'",
              "instance": "/v2/mode"
            }""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectRestoreWhenCallerIsNotAuthorized() {
    // given
    Mockito.when(recoveryServices.restore(Mockito.any(), Mockito.anyBoolean(), Mockito.any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.createForbiddenException(BACKUP_RESTORE_AUTHORIZATION)));

    // when / then
    webClient
        .post()
        .uri("/v2/restore")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100]}")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 403,
              "title": "FORBIDDEN",
              "detail": "Unauthorized to perform operation 'RESTORE' on resource 'BACKUP'",
              "instance": "/v2/restore"
            }""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldMapInvalidRequestErrorFromCoordinator() {
    // given
    Mockito.when(recoveryServices.restore(Mockito.any(), Mockito.anyBoolean(), Mockito.any()))
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
    Mockito.when(recoveryServices.restore(Mockito.any(), Mockito.anyBoolean(), Mockito.any()))
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
    Mockito.when(recoveryServices.restore(Mockito.any(), Mockito.anyBoolean(), Mockito.any()))
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
  void shouldRejectRestoreStatusWhenCallerIsNotAuthorized() {
    // given
    Mockito.when(recoveryServices.restoreStatus(Mockito.any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.createForbiddenException(BACKUP_RESTORE_AUTHORIZATION)));

    // when / then
    webClient
        .get()
        .uri("/v2/restore")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 403,
              "title": "FORBIDDEN",
              "detail": "Unauthorized to perform operation 'RESTORE' on resource 'BACKUP'",
              "instance": "/v2/restore"
            }""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnNotFoundWhenPhysicalTenantNotFound() {
    // given
    final var partitionGroups = Map.<String, PartitionGroupConfiguration>of();

    // when
    Mockito.when(recoveryServices.restoreStatus(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    new CurrentClusterConfiguration(
                        1,
                        GlobalConfiguration.init(),
                        partitionGroups,
                        PhasedChangeState.empty()))));

    // then
    expectRestoreStatusProblem(
        HttpStatus.NOT_FOUND, "NOT_FOUND", "No configuration found for physical tenant 'default'");
  }

  @Test
  void shouldReturnNotFoundWhenNoRestore() {
    // given
    final var partitionGroups =
        Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, PartitionGroupConfiguration.empty(1));

    // when
    Mockito.when(recoveryServices.restoreStatus(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    new CurrentClusterConfiguration(
                        1,
                        GlobalConfiguration.init(),
                        partitionGroups,
                        PhasedChangeState.empty()))));

    // then
    expectRestoreStatusProblem(
        HttpStatus.NOT_FOUND, "NOT_FOUND", "No restore is currently in progress");
  }

  @Test
  void shouldReturnInProgressRestoreStatus() {
    // given
    final var broker = MemberId.from("1");
    final var startedAt = Instant.parse("2024-01-01T10:00:00Z");
    final var plan =
        new DependencyChangePlan(
            -2L,
            Status.IN_PROGRESS,
            startedAt,
            OperationGraph.sequential(
                List.<ClusterConfigurationChangeOperation>of(
                    new PartitionPreRestoreOperation(broker, 1),
                    new PartitionRestoreOperation(broker, 1, new TreeSet<>(List.of(10L, 11L))),
                    new ModeChangeOperation(broker, Mode.PROCESSING))),
            new TreeMap<>(
                Map.of(
                    new OperationId(0),
                    startedAt.plusSeconds(1),
                    new OperationId(1),
                    startedAt.plusSeconds(2))));
    stubTopology(Optional.empty(), Optional.of(plan));

    // when
    final var response =
        webClient
            .get()
            .uri("/v2/restore")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(RestoreStatusResponse.class)
            .returnResult()
            .getResponseBody();

    // then
    final var expected =
        RestoreStatusResponse.Builder.create()
            .status(RestoreStatusResponse.StatusEnum.IN_PROGRESS)
            .changeId("-2")
            .startedAt("2024-01-01T10:00:00Z")
            .brokers(
                List.of(
                    RestoreBrokerStatus.Builder.create()
                        .brokerId("1")
                        .partitionsRestored(1)
                        .partitionsToRestore(1)
                        .partitions(
                            List.of(
                                RestorePartitionStatus.Builder.create()
                                    .partitionId(1)
                                    .state(RestorePartitionStatus.StateEnum.RESTORED)
                                    .backupIds(List.of(10L, 11L))
                                    .completedAt("2024-01-01T10:00:02Z")
                                    .build()))
                        .build()))
            .build();

    assertThat(response).isEqualTo(expected);
  }

  @Test
  void shouldReturnNotFoundOnceRestoreHasCompleted() {
    // given
    final var startedAt = Instant.parse("2024-01-01T10:00:00Z");
    final var lastChange =
        new CompletedChange(-2L, Status.COMPLETED, startedAt, startedAt.plusSeconds(300));
    stubTopology(Optional.of(lastChange), Optional.empty());

    // when / then
    expectNoRestoreInProgress();
  }

  @Test
  void shouldReturnNotFoundForUnrelatedPendingModeTransition() {
    // given
    final var broker = MemberId.from("1");
    final var plan =
        new DependencyChangePlan(
            42L,
            Status.IN_PROGRESS,
            Instant.parse("2024-01-01T10:00:00Z"),
            OperationGraph.sequential(
                List.<ClusterConfigurationChangeOperation>of(
                    new ModeChangeOperation(broker, Mode.PROCESSING))),
            Collections.emptySortedMap());
    stubTopology(Optional.empty(), Optional.of(plan));

    // when / then
    expectNoRestoreInProgress();
  }

  @Test
  void shouldMapErrorResponseWhenTopologyQueryRejected() {
    // given
    Mockito.when(recoveryServices.restoreStatus(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(
                    new ErrorResponse(ErrorCode.INTERNAL_ERROR, "topology is unavailable"))));

    // when / then
    expectRestoreStatusProblem(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "topology is unavailable");
  }

  @Test
  void shouldMapFailedTopologyRequest() {
    // given
    Mockito.when(recoveryServices.restoreStatus(Mockito.any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("no coordinator")));

    // when / then
    expectRestoreStatusProblem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        RuntimeException.class.getName(),
        "Unexpected error occurred during the request processing: no coordinator");
  }

  private void expectNoRestoreInProgress() {
    expectRestoreStatusProblem(
        HttpStatus.NOT_FOUND, "NOT_FOUND", "No restore is currently in progress");
  }

  /** Asserts that {@code GET /v2/restore} answers with the given RFC 9457 problem detail. */
  private void expectRestoreStatusProblem(
      final HttpStatus status, final String title, final String detail) {
    webClient
        .get()
        .uri("/v2/restore")
        .exchange()
        .expectStatus()
        .isEqualTo(status)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": %d,
              "title": "%s",
              "detail": "%s",
              "instance": "/v2/restore"
            }"""
                .formatted(status.value(), title, detail),
            JsonCompareMode.STRICT);
  }

  private void stubTopology(
      final Optional<CompletedChange> lastChange,
      final Optional<DependencyChangePlan> pendingChanges) {
    final var configuration =
        new CurrentClusterConfiguration(
            2,
            new GlobalConfiguration(
                6,
                Optional.empty(),
                Map.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()),
            Map.of(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                new PartitionGroupConfiguration(
                    1, 1, Map.of(), Optional.empty(), pendingChanges, lastChange)),
            new PhasedChangeState(1, Map.of(), List.of()));
    Mockito.when(recoveryServices.restoreStatus(Mockito.any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(configuration)));
  }

  // The database type and continuous-backups flag are no longer resolved here: RecoveryServices
  // (mocked above) now binds them per physical tenant. The controller only has to forward the
  // backup selection and dryRun untouched, regardless of secondary storage — see
  // RecoveryServicesTest#shouldStampItsBoundRestoreEnvironmentIntoTheRequest for that part.

  @ParameterizedTest
  @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
  void shouldForwardBackupIds(final String baseUrl) {
    // given
    stubRestoreAccepted();

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

    Mockito.verify(recoveryServices)
        .restore(eq(new RestoreParameters(List.of(100L, 101L), null, null)), eq(false), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
  void shouldForwardNoParameters(final String baseUrl) {
    // given
    stubRestoreAccepted();

    // when / then
    webClient
        .post()
        .uri(baseUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isAccepted();

    Mockito.verify(recoveryServices)
        .restore(eq(new RestoreParameters(List.of(), null, null)), eq(false), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
  void shouldForwardTimeRange(final String baseUrl) {
    // given
    stubRestoreAccepted();

    // when / then
    webClient
        .post()
        .uri(baseUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"from\": \"2024-01-01T10:00:00Z\", \"to\": \"2024-01-01T12:00:00Z\"}")
        .exchange()
        .expectStatus()
        .isAccepted();

    Mockito.verify(recoveryServices)
        .restore(
            eq(new RestoreParameters(List.of(), "2024-01-01T10:00:00Z", "2024-01-01T12:00:00Z")),
            eq(false),
            any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
  void shouldForwardDryRun(final String baseUrl) {
    // given
    stubRestoreAccepted();

    // when / then
    webClient
        .post()
        .uri(baseUrl + "?dryRun=true")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100]}")
        .exchange()
        .expectStatus()
        .isAccepted();

    Mockito.verify(recoveryServices)
        .restore(eq(new RestoreParameters(List.of(100L), null, null)), eq(true), any());
  }
}

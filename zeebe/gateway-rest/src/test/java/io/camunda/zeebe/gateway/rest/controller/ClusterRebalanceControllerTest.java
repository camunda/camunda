/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.service.ClusterRebalanceServices;
import io.camunda.service.ClusterRebalanceServices.ClusterRebalanceRequest;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.rebalance.CancelRebalanceResponse;
import io.camunda.zeebe.rebalance.ClusterLeadershipStatus;
import io.camunda.zeebe.rebalance.PartitionLeadershipStatus;
import io.camunda.zeebe.rebalance.PartitionRebalance;
import io.camunda.zeebe.rebalance.PartitionRebalanceOutcome;
import io.camunda.zeebe.rebalance.PartitionRebalanceProgress;
import io.camunda.zeebe.rebalance.RebalanceOutcome;
import io.camunda.zeebe.rebalance.RebalanceOverrides;
import io.camunda.zeebe.rebalance.RebalanceStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(ClusterRebalanceController.class)
class ClusterRebalanceControllerTest extends RestControllerTest {

  private static final String REBALANCE_URL = "/cluster/v2/rebalance";

  @MockitoBean ClusterRebalanceServices clusterRebalanceServices;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.clusterRebalanceServices()).thenReturn(clusterRebalanceServices);
  }

  @Test
  void shouldTriggerWithConfiguredDefaultsWhenNoBodyIsGiven() {
    // given
    when(clusterRebalanceServices.triggerRebalance(
            ClusterRebalanceRequest.withDefaultSettings(false)))
        .thenReturn(CompletableFuture.completedFuture(idleStatus()));

    // when / then
    webClient.post().uri(REBALANCE_URL).exchange().expectStatus().isAccepted();

    verify(clusterRebalanceServices)
        .triggerRebalance(ClusterRebalanceRequest.withDefaultSettings(false));
  }

  @Test
  void shouldMapEveryOverrideOnTrigger() {
    // given
    final var captor = ArgumentCaptor.forClass(ClusterRebalanceRequest.class);
    when(clusterRebalanceServices.triggerRebalance(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(idleStatus()));

    // when
    webClient
        .post()
        .uri(REBALANCE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "replicationLagThreshold": 8388608,
              "replicationTimeout": "PT10S",
              "maxTransferAttempts": 3,
              "leaderWaitTimeout": "PT1M"
            }
            """)
        .exchange()
        .expectStatus()
        .isAccepted();

    // then
    assertThat(captor.getValue())
        .isEqualTo(
            new ClusterRebalanceRequest(
                false, 8388608L, Duration.ofSeconds(10), 3, Duration.ofMinutes(1)));
  }

  @Test
  void shouldPreserveDryRunSemantics() {
    // given
    final var captor = ArgumentCaptor.forClass(ClusterRebalanceRequest.class);
    when(clusterRebalanceServices.triggerRebalance(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(idleStatus()));

    // when
    webClient.post().uri(REBALANCE_URL + "?dryRun=true").exchange().expectStatus().isAccepted();

    // then
    assertThat(captor.getValue().dryRun()).isTrue();
  }

  @Test
  void shouldReportADryRunTriggerResponseAsThePlanUnderRunningRebalance() {
    // given
    final var member1 = MemberId.from("1");
    final var member2 = MemberId.from("2");
    final var previousCompletion =
        new RebalanceStatus.Completed(
            6L, RebalanceOutcome.COMPLETED, List.of(), Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
    final var dryRunResponse =
        new RebalanceStatus(
            new RebalanceStatus.Running(
                7L,
                RebalanceOverrides.none(),
                true,
                false,
                List.of(
                    new PartitionRebalance(
                        "default", 1, member1, member2, PartitionRebalanceProgress.PENDING)),
                Instant.EPOCH.plusSeconds(2)),
            previousCompletion,
            ClusterLeadershipStatus.aggregateOf(List.of()));
    when(clusterRebalanceServices.triggerRebalance(
            new ClusterRebalanceRequest(true, null, null, null, null)))
        .thenReturn(CompletableFuture.completedFuture(dryRunResponse));

    // when / then
    webClient
        .post()
        .uri(REBALANCE_URL + "?dryRun=true")
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .json(
            """
            {
              "state": "BALANCED",
              "partitions": [],
              "runningRebalance": {
                "rebalanceId": 7,
                "dryRun": true,
                "startedAt": "1970-01-01T00:00:02.000Z",
                "cancelRequested": false,
                "partitions": [
                  { "partitionId": 1, "physicalTenantId": "default", "currentLeader": "1", "desiredLeader": "2", "progress": "PENDING", "result": null }
                ]
              },
              "lastCompletedRebalance": {
                "rebalanceId": 6,
                "startedAt": "1970-01-01T00:00:00.000Z",
                "finishedAt": "1970-01-01T00:00:01.000Z",
                "result": "COMPLETED",
                "partitions": []
              }
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectAnInvalidDurationSyntax() {
    // when / then
    webClient
        .post()
        .uri(REBALANCE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"replicationTimeout\": \"not-a-duration\"}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @ParameterizedTest
  @MethodSource("zeroOrNegativeDurations")
  void shouldRejectAZeroOrNegativeDuration(final String field, final String value) {
    // when / then
    webClient
        .post()
        .uri(REBALANCE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"%s\": \"%s\"}".formatted(field, value))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  private static List<Arguments> zeroOrNegativeDurations() {
    return List.of(
        Arguments.of("replicationTimeout", "PT0S"),
        Arguments.of("replicationTimeout", "-PT10S"),
        Arguments.of("leaderWaitTimeout", "PT0S"),
        Arguments.of("leaderWaitTimeout", "-PT1M"));
  }

  @Test
  void shouldRejectANonPositiveMaxTransferAttempts() {
    // when / then
    webClient
        .post()
        .uri(REBALANCE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"maxTransferAttempts\": 0}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldRejectANegativeReplicationLagThreshold() {
    // when / then
    webClient
        .post()
        .uri(REBALANCE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"replicationLagThreshold\": -1}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldReportTheCompleteLiveBalanceAndCompletedHistory() {
    // given
    final var memberA = MemberId.from("0");
    final var memberB = MemberId.from("1");
    final var status =
        new RebalanceStatus(
            null,
            new RebalanceStatus.Completed(
                7L,
                RebalanceOutcome.COMPLETED,
                List.of(
                    new PartitionRebalance(
                        "default",
                        2,
                        memberA,
                        memberB,
                        PartitionRebalanceProgress.COMPLETED,
                        PartitionRebalanceOutcome.TRANSFERRED)),
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(5)),
            new ClusterLeadershipStatus(
                ClusterLeadershipStatus.State.UNBALANCED,
                List.of(
                    new PartitionLeadershipStatus(
                        "default",
                        1,
                        memberA,
                        memberB,
                        PartitionLeadershipStatus.State.TRANSFERRING))));
    when(clusterRebalanceServices.getRebalanceStatus())
        .thenReturn(CompletableFuture.completedFuture(status));

    // when / then
    webClient
        .get()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "state": "UNBALANCED",
              "partitions": [
                { "partitionId": 1, "physicalTenantId": "default", "currentLeader": "0", "desiredLeader": "1", "state": "TRANSFERRING" }
              ],
              "runningRebalance": null,
              "lastCompletedRebalance": {
                "rebalanceId": 7,
                "startedAt": "1970-01-01T00:00:00.000Z",
                "finishedAt": "1970-01-01T00:00:05.000Z",
                "result": "COMPLETED",
                "partitions": [
                  { "partitionId": 2, "physicalTenantId": "default", "currentLeader": "0", "desiredLeader": "1", "progress": "COMPLETED", "result": "TRANSFERRED" }
                ]
              }
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldReportTheRunningRebalanceSeparatelyFromLiveBalance() {
    // given
    final var member0 = MemberId.from("0");
    final var member1 = MemberId.from("1");
    final var member2 = MemberId.from("2");
    final var status =
        new RebalanceStatus(
            new RebalanceStatus.Running(
                8L,
                RebalanceOverrides.none(),
                false,
                true,
                List.of(
                    new PartitionRebalance(
                        "default", 1, member1, member2, PartitionRebalanceProgress.TRANSFERRING),
                    new PartitionRebalance(
                        "default",
                        2,
                        member2,
                        member2,
                        PartitionRebalanceProgress.COMPLETED,
                        PartitionRebalanceOutcome.ALREADY_LEADER)),
                Instant.EPOCH.plusSeconds(3)),
            null,
            new ClusterLeadershipStatus(
                ClusterLeadershipStatus.State.UNBALANCED,
                List.of(
                    new PartitionLeadershipStatus(
                        "default",
                        1,
                        member2,
                        member0,
                        PartitionLeadershipStatus.State.UNBALANCED))));
    when(clusterRebalanceServices.getRebalanceStatus())
        .thenReturn(CompletableFuture.completedFuture(status));

    // when / then
    webClient
        .get()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "state": "UNBALANCED",
              "partitions": [
                { "partitionId": 1, "physicalTenantId": "default", "currentLeader": "2", "desiredLeader": "0", "state": "UNBALANCED" }
              ],
              "runningRebalance": {
                "rebalanceId": 8,
                "dryRun": false,
                "startedAt": "1970-01-01T00:00:03.000Z",
                "cancelRequested": true,
                "partitions": [
                  { "partitionId": 1, "physicalTenantId": "default", "currentLeader": "1", "desiredLeader": "2", "progress": "TRANSFERRING", "result": null },
                  { "partitionId": 2, "physicalTenantId": "default", "currentLeader": "2", "desiredLeader": "2", "progress": "COMPLETED", "result": "ALREADY_LEADER" }
                ]
              },
              "lastCompletedRebalance": null
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldOmitCompletedHistoryWhenAbsent() {
    // given
    when(clusterRebalanceServices.getRebalanceStatus())
        .thenReturn(CompletableFuture.completedFuture(idleStatus()));

    // when / then
    webClient
        .get()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "state": "BALANCED",
              "partitions": [],
              "runningRebalance": null,
              "lastCompletedRebalance": null
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldOmitCurrentLeaderWhenAbsent() {
    // given
    final var status =
        new RebalanceStatus(
            null,
            null,
            new ClusterLeadershipStatus(
                ClusterLeadershipStatus.State.UNBALANCED,
                List.of(
                    new PartitionLeadershipStatus(
                        "default",
                        1,
                        null,
                        MemberId.from("1"),
                        PartitionLeadershipStatus.State.UNBALANCED))));
    when(clusterRebalanceServices.getRebalanceStatus())
        .thenReturn(CompletableFuture.completedFuture(status));

    // when / then
    webClient
        .get()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "state": "UNBALANCED",
              "partitions": [
                { "partitionId": 1, "physicalTenantId": "default", "currentLeader": null, "desiredLeader": "1", "state": "UNBALANCED" }
              ],
              "runningRebalance": null,
              "lastCompletedRebalance": null
            }
            """,
            JsonCompareMode.STRICT);
  }

  @ParameterizedTest
  @MethodSource("allPartitionOutcomes")
  void shouldMapEveryCompletedPartitionOutcomeToTheDocumentedEnum(
      final PartitionRebalanceOutcome outcome, final String documented) {
    // given
    final var status =
        new RebalanceStatus(
            null,
            new RebalanceStatus.Completed(
                7L,
                RebalanceOutcome.COMPLETED,
                List.of(
                    new PartitionRebalance(
                        "default",
                        1,
                        MemberId.from("0"),
                        MemberId.from("1"),
                        PartitionRebalanceProgress.COMPLETED,
                        outcome)),
                Instant.EPOCH,
                Instant.EPOCH),
            ClusterLeadershipStatus.aggregateOf(List.of()));
    when(clusterRebalanceServices.getRebalanceStatus())
        .thenReturn(CompletableFuture.completedFuture(status));

    // when / then
    webClient
        .get()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.lastCompletedRebalance.partitions[0].result")
        .isEqualTo(documented);
  }

  private static List<Arguments> allPartitionOutcomes() {
    return List.of(
        Arguments.of(PartitionRebalanceOutcome.TRANSFERRED, "TRANSFERRED"),
        Arguments.of(PartitionRebalanceOutcome.ALREADY_LEADER, "ALREADY_LEADER"),
        Arguments.of(PartitionRebalanceOutcome.NOT_MEMBER, "NOT_MEMBER"),
        Arguments.of(PartitionRebalanceOutcome.NOT_REPLICATING, "NOT_REPLICATING"),
        Arguments.of(PartitionRebalanceOutcome.UNREACHABLE, "UNREACHABLE"),
        Arguments.of(PartitionRebalanceOutcome.NOT_COORDINATOR, "NOT_COORDINATOR"),
        Arguments.of(PartitionRebalanceOutcome.STALE_CONFIGURATION, "STALE_CONFIGURATION"),
        Arguments.of(PartitionRebalanceOutcome.TRANSFER_IN_PROGRESS, "TRANSFER_IN_PROGRESS"),
        Arguments.of(PartitionRebalanceOutcome.LAG_TOO_HIGH, "LAG_TOO_HIGH"),
        Arguments.of(PartitionRebalanceOutcome.LEADER_INITIALIZING, "LEADER_INITIALIZING"),
        Arguments.of(
            PartitionRebalanceOutcome.CONFIGURATION_CHANGE_IN_PROGRESS,
            "CONFIGURATION_CHANGE_IN_PROGRESS"),
        Arguments.of(PartitionRebalanceOutcome.PAUSE_FAILED, "PAUSE_FAILED"),
        Arguments.of(PartitionRebalanceOutcome.REPLICATION_TIMED_OUT, "REPLICATION_TIMED_OUT"),
        Arguments.of(PartitionRebalanceOutcome.TIMEOUT_NOW_EXHAUSTED, "TIMEOUT_NOW_EXHAUSTED"),
        Arguments.of(PartitionRebalanceOutcome.LEADER_CHANGED, "LEADER_CHANGED"),
        Arguments.of(PartitionRebalanceOutcome.NO_LEADER, "NO_LEADER"),
        Arguments.of(PartitionRebalanceOutcome.NO_RESPONSE, "NO_RESPONSE"),
        Arguments.of(PartitionRebalanceOutcome.CANCELLED, "CANCELLED"),
        Arguments.of(
            PartitionRebalanceOutcome.PHYSICAL_TENANT_DISABLED, "PHYSICAL_TENANT_DISABLED"));
  }

  @ParameterizedTest
  @MethodSource("allBalanceStates")
  void shouldMapEveryClusterLeadershipStateToTheDocumentedEnum(
      final ClusterLeadershipStatus.State state, final String documented) {
    // given
    when(clusterRebalanceServices.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.completedFuture(
                new RebalanceStatus(null, null, new ClusterLeadershipStatus(state, List.of()))));

    // when / then
    webClient
        .get()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.state")
        .isEqualTo(documented);
  }

  private static List<Arguments> allBalanceStates() {
    return List.of(
        Arguments.of(ClusterLeadershipStatus.State.BALANCED, "BALANCED"),
        Arguments.of(ClusterLeadershipStatus.State.BALANCING, "BALANCING"),
        Arguments.of(ClusterLeadershipStatus.State.UNBALANCED, "UNBALANCED"));
  }

  @Test
  void shouldReportThatARunningRebalanceWasCancelled() {
    // given
    when(clusterRebalanceServices.cancelRebalance())
        .thenReturn(CompletableFuture.completedFuture(new CancelRebalanceResponse(true)));

    // when / then
    webClient
        .delete()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.wasRunning")
        .isEqualTo(true);
  }

  @Test
  void shouldReportThatNoRebalanceWasRunningToCancel() {
    // given
    when(clusterRebalanceServices.cancelRebalance())
        .thenReturn(CompletableFuture.completedFuture(new CancelRebalanceResponse(false)));

    // when / then
    webClient
        .delete()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.wasRunning")
        .isEqualTo(false);
  }

  @Test
  void shouldMapAConflictToHttp409() {
    // given
    when(clusterRebalanceServices.triggerRebalance(
            ClusterRebalanceRequest.withDefaultSettings(false)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("a rebalance is already running", Status.ALREADY_EXISTS)));

    // when / then
    webClient.post().uri(REBALANCE_URL).exchange().expectStatus().isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void shouldMapAnUnavailableCoordinatorToHttp503() {
    // given
    when(clusterRebalanceServices.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("no coordinator is available", Status.UNAVAILABLE)));

    // when / then
    webClient
        .get()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void shouldMapATimeoutToHttp504() {
    // given
    when(clusterRebalanceServices.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException(
                    "the coordinator did not answer in time", Status.DEADLINE_EXCEEDED)));

    // when / then
    webClient
        .get()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
  }

  @Test
  void shouldMapAnInternalCoordinatorFailureToHttp500() {
    // given
    when(clusterRebalanceServices.cancelRebalance())
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("the coordinator failed internally", Status.INTERNAL)));

    // when / then
    webClient
        .delete()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void shouldMapAnUnusableCoordinatorReplyToHttp502() {
    // given
    when(clusterRebalanceServices.cancelRebalance())
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("the coordinator gave no answer", Status.ABORTED)));

    // when / then
    webClient
        .delete()
        .uri(REBALANCE_URL)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  private static RebalanceStatus idleStatus() {
    return RebalanceStatus.idle();
  }
}

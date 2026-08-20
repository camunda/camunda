/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.service.ClusterRecoveryServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse.CurrentConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(ClusterRecoveryController.class)
class ClusterRecoveryControllerTest extends RestControllerTest {

  private static final String MODE_URL = "/cluster/v2/mode";
  private static final String RESTORE_URL = "/cluster/v2/restore";

  @MockitoBean ClusterRecoveryServices clusterRecoveryServices;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.clusterRecoveryServices()).thenReturn(clusterRecoveryServices);
  }

  @Test
  void shouldReportThePlannedChangeCoveringEveryPhysicalTenant() {
    // given — a plan that transitions two physical tenants at once
    when(clusterRecoveryServices.changeMode(
            Mockito.isNull(), Mockito.eq(Mode.RECOVERING), Mockito.eq(false)))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(plannedChange(7L, "tenant-b", "default"))));

    // when / then — every tenant keeps its own operations, ordered by tenant so that the response
    // does not depend on the iteration order of the plan
    webClient
        .patch()
        .uri(MODE_URL + "?mode=RECOVERING")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "changeId": "7",
              "plannedChanges": [
                {
                  "physicalTenantId": "default",
                  "operations": [
                    { "operation": "ModeChangeOperation", "mode": "RECOVERING" }
                  ]
                },
                {
                  "physicalTenantId": "tenant-b",
                  "operations": [
                    { "operation": "ModeChangeOperation", "mode": "RECOVERING" }
                  ]
                }
              ]
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldChangeModeOfTheRequestedPhysicalTenantOnly() {
    // given
    when(clusterRecoveryServices.changeMode(
            Mockito.eq("tenant-b"), Mockito.eq(Mode.RECOVERING), Mockito.eq(false)))
        .thenReturn(CompletableFuture.completedFuture(Either.right(plannedChange(8L, "tenant-b"))));

    // when / then — the plan names the requested tenant and no other
    webClient
        .patch()
        .uri(MODE_URL + "?mode=RECOVERING&physicalTenantId=tenant-b")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "changeId": "8",
              "plannedChanges": [
                {
                  "physicalTenantId": "tenant-b",
                  "operations": [
                    { "operation": "ModeChangeOperation", "mode": "RECOVERING" }
                  ]
                }
              ]
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldValidateWithoutApplyingWhenDryRunIsRequested() {
    // given
    when(clusterRecoveryServices.changeMode(
            Mockito.isNull(), Mockito.eq(Mode.PROCESSING), Mockito.eq(true)))
        .thenReturn(CompletableFuture.completedFuture(Either.right(plannedChange(7L))));

    // when / then
    webClient
        .patch()
        .uri(MODE_URL + "?mode=PROCESSING&dryRun=true")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void shouldMapRejectedModeChangeToClientError() {
    // given
    when(clusterRecoveryServices.changeMode(
            Mockito.isNull(), Mockito.eq(Mode.RECOVERING), Mockito.eq(false)))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(
                    new ErrorResponse(
                        ErrorCode.INVALID_REQUEST, "this cluster has a single partition group"))));

    // when / then
    webClient.patch().uri(MODE_URL + "?mode=RECOVERING").exchange().expectStatus().isBadRequest();
  }

  @Test
  void shouldMapUnknownPhysicalTenantToNotFound() {
    // given
    when(clusterRecoveryServices.changeMode(
            Mockito.eq("tenant-b"), Mockito.eq(Mode.RECOVERING), Mockito.eq(false)))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.NOT_FOUND, "it has no partition group"))));

    // when / then
    webClient
        .patch()
        .uri(MODE_URL + "?mode=RECOVERING&physicalTenantId=tenant-b")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void shouldChangeModeOfEveryPhysicalTenantWhenTheGivenTenantIsBlank() {
    // given
    when(clusterRecoveryServices.changeMode(
            Mockito.isNull(), Mockito.eq(Mode.RECOVERING), Mockito.eq(false)))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(plannedChange(8L, "tenant-b", "default"))));

    // when / then — a blank id names no tenant, so the operation spans the cluster instead of being
    // rejected
    webClient
        .patch()
        .uri(MODE_URL + "?mode=RECOVERING&physicalTenantId=")
        .exchange()
        .expectStatus()
        .isOk();

    Mockito.verify(clusterRecoveryServices).changeMode(null, Mode.RECOVERING, false);
  }

  @Test
  void shouldRestoreEveryPhysicalTenantWhenTheGivenTenantIsBlank() {
    // given
    givenRestoreAccepted(9L);

    // when / then — the parameter both operations share resolves the same way
    webClient
        .post()
        .uri(RESTORE_URL + "?physicalTenantId=")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [ 55 ]}")
        .exchange()
        .expectStatus()
        .isAccepted();

    Mockito.verify(clusterRecoveryServices)
        .restore(
            Optional.empty(), new RestoreParameters(List.of(55L), null, null), Map.of(), false);
  }

  @Test
  void shouldRestoreEveryPhysicalTenantWithTheSameParametersWhenNoOverridesAreGiven() {
    // given
    givenRestoreAccepted(9L);

    // when
    webClient
        .post()
        .uri(RESTORE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100, 101]}")
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .jsonPath("$.changeId")
        .isEqualTo("9");

    // then
    Mockito.verify(clusterRecoveryServices)
        .restore(
            Optional.empty(),
            new RestoreParameters(List.of(100L, 101L), null, null),
            Map.of(),
            false);
  }

  @Test
  void shouldRestoreTheOverriddenPhysicalTenantsWithTheirOwnParameters() {
    // given
    givenRestoreAccepted(9L);

    // when
    webClient
        .post()
        .uri(RESTORE_URL + "?dryRun=true")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "backupIds": [ 100 ],
              "overrides": {
                "tenant-b": { "backupIds": [ 55 ] },
                "tenant-c": { "from": "2024-01-01T10:00:00Z", "to": "2024-01-01T12:00:00Z" }
              }
            }
            """)
        .exchange()
        .expectStatus()
        .isAccepted();

    // then
    Mockito.verify(clusterRecoveryServices)
        .restore(
            Optional.empty(),
            new RestoreParameters(List.of(100L), null, null),
            Map.of(
                "tenant-b",
                new RestoreParameters(List.of(55L), null, null),
                "tenant-c",
                new RestoreParameters(List.of(), "2024-01-01T10:00:00Z", "2024-01-01T12:00:00Z")),
            true);
  }

  @Test
  void shouldRestoreTheRequestedPhysicalTenantOnly() {
    // given
    givenRestoreAccepted(9L);

    // when
    webClient
        .post()
        .uri(RESTORE_URL + "?physicalTenantId=tenant-b")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [ 55 ]}")
        .exchange()
        .expectStatus()
        .isAccepted();

    // then
    Mockito.verify(clusterRecoveryServices)
        .restore(
            Optional.of("tenant-b"),
            new RestoreParameters(List.of(55L), null, null),
            Map.of(),
            false);
  }

  @Test
  void shouldRejectOverridesOnATenantScopedRestore() {
    // when / then
    webClient
        .post()
        .uri(RESTORE_URL + "?physicalTenantId=tenant-b")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"backupIds\": [ 55 ], \"overrides\": { \"tenant-c\": { \"backupIds\": [1] } }}")
        .exchange()
        .expectStatus()
        .isBadRequest();

    Mockito.verify(clusterRecoveryServices, Mockito.never())
        .restore(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean());
  }

  @Test
  void shouldReportEachPlannedRestoreOperationWithOnlyThePropertiesItCarries() {
    // given — the plan a restore produces: a partition is pre-restored, restored from its backups,
    // the broker is returned to processing, and its incarnation number is updated
    final var broker = MemberId.from("1");
    when(clusterRecoveryServices.restore(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    plannedChange(
                        9L,
                        PartitionGroupPhase.sequential(
                            Map.of(
                                "default",
                                List.of(
                                    new PartitionPreRestoreOperation(broker, 1),
                                    new PartitionRestoreOperation(
                                        broker, 1, new TreeSet<>(List.of(100L, 101L))),
                                    new ModeChangeOperation(broker, Mode.PROCESSING),
                                    new AwaitModeChangeOperation(broker, Mode.PROCESSING),
                                    new UpdateIncarnationNumberOperation(broker))))))));

    // when / then — an operator reviewing the plan sees which backups land on which partition, and
    // each operation reports only what it carries: no null partition on a mode change, no empty
    // backups on an operation that restores nothing
    webClient
        .post()
        .uri(RESTORE_URL + "?dryRun=true")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100, 101]}")
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .json(
            """
            {
              "changeId": "9",
              "plannedChanges": [
                {
                  "physicalTenantId": "default",
                  "operations": [
                    {
                      "operation": "PartitionPreRestoreOperation",
                      "brokerId": "1",
                      "partitionId": 1
                    },
                    {
                      "operation": "PartitionRestoreOperation",
                      "brokerId": "1",
                      "partitionId": 1,
                      "backupIds": [ 100, 101 ]
                    },
                    {
                      "operation": "ModeChangeOperation",
                      "brokerId": "1",
                      "mode": "PROCESSING"
                    },
                    {
                      "operation": "AwaitModeChangeOperation",
                      "brokerId": "1",
                      "mode": "PROCESSING"
                    },
                    {
                      "operation": "UpdateIncarnationNumberOperation",
                      "brokerId": "1"
                    }
                  ]
                }
              ]
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldMapARestoreOfAnUnknownPhysicalTenantToNotFound() {
    // given
    when(clusterRecoveryServices.restore(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(
                    new ErrorResponse(
                        ErrorCode.NOT_FOUND, "no physical tenant is configured for: tenant-x"))));

    // when / then
    webClient
        .post()
        .uri(RESTORE_URL + "?physicalTenantId=tenant-x")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [ 55 ]}")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void shouldMapRestoreOutsideOfRecoveryModeToConflict() {
    // given
    when(clusterRecoveryServices.restore(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(
                    new ErrorResponse(
                        ErrorCode.INVALID_STATE, "the cluster is not in recovery mode"))));

    // when / then
    webClient
        .post()
        .uri(RESTORE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [ 100 ]}")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT);
  }

  private void givenRestoreAccepted(final long changeId) {
    when(clusterRecoveryServices.restore(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(plannedChange(changeId))));
  }

  /**
   * A mode change plan that transitions each of the given physical tenants, defaulting to the
   * single-tenant plan a cluster without additional partition groups produces.
   */
  private ClusterConfigurationChangeResponse plannedChange(
      final long changeId, final String... physicalTenantIds) {
    final var tenants =
        physicalTenantIds.length == 0 ? new String[] {"default"} : physicalTenantIds;
    final Map<String, List<PartitionGroupOperation>> groupOperations = new HashMap<>();
    for (final var physicalTenantId : tenants) {
      groupOperations.put(
          physicalTenantId, List.of(new ModeChangeOperation(MemberId.from("0"), Mode.RECOVERING)));
    }
    return plannedChange(changeId, PartitionGroupPhase.sequential(groupOperations));
  }

  private ClusterConfigurationChangeResponse plannedChange(
      final long changeId, final PartitionGroupPhase phase) {
    final var flatOperations =
        phase.groupOperations().values().stream()
            .flatMap(List::stream)
            .map(ClusterConfigurationChangeOperation.class::cast)
            .toList();
    return new ClusterConfigurationChangeResponse(
        changeId,
        new LegacyConfigurationChangeResponse(Map.of(), Map.of(), flatOperations),
        new CurrentConfigurationChangeResponse(
            CurrentClusterConfiguration.uninitialized(),
            CurrentClusterConfiguration.uninitialized(),
            List.of(phase)));
  }
}

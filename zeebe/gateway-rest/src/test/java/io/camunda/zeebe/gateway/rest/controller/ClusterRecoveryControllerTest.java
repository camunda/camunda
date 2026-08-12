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
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(ClusterRecoveryController.class)
class ClusterRecoveryControllerTest extends RestControllerTest {

  private static final String MODE_URL = "/cluster/v2/mode";

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
  void shouldRejectABlankPhysicalTenantAsAMalformedRequest() {
    // when / then
    webClient
        .patch()
        .uri(MODE_URL + "?mode=RECOVERING&physicalTenantId=")
        .exchange()
        .expectStatus()
        .isBadRequest();

    Mockito.verify(clusterRecoveryServices, Mockito.never())
        .changeMode(Mockito.any(), Mockito.any(), Mockito.anyBoolean());
  }

  private ClusterConfigurationChangeResponse plannedChange(
      final long changeId, final String... physicalTenantIds) {
    final var operation = new ModeChangeOperation(MemberId.from("0"), Mode.RECOVERING);
    final Map<String, List<PartitionGroupOperation>> operationsPerTenant = new HashMap<>();
    for (final var physicalTenantId : physicalTenantIds) {
      operationsPerTenant.put(physicalTenantId, List.of(operation));
    }
    return new ClusterConfigurationChangeResponse(
        changeId,
        new LegacyConfigurationChangeResponse(
            Map.of(), Map.of(), List.<ClusterConfigurationChangeOperation>of(operation)),
        new CurrentConfigurationChangeResponse(
            CurrentClusterConfiguration.uninitialized(),
            CurrentClusterConfiguration.uninitialized(),
            List.of(new PartitionGroupParallelPhase(operationsPerTenant))));
  }
}

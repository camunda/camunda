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
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.shared.management.FlowControlEndpoint.FlowControlService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;

final class FlowControlEndpointTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private final FlowControlService service = mock(FlowControlService.class);

  @Test
  void shouldKeepFlatShapeOnSingleTenantCluster() {
    // given
    final var endpoint = endpointFor(Set.of(DEFAULT_PHYSICAL_TENANT_ID));
    when(service.get(DEFAULT_PHYSICAL_TENANT_ID)).thenReturn(configuration(1, 50));

    // when
    final var response = endpoint.get(null);

    // then - a single-tenant cluster sees the pre-existing, partition-keyed response
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(Map.of(1, limit(50)));
  }

  @Test
  void shouldKeyByPhysicalTenantWhenSeveralAreKnown() {
    // given
    final var endpoint = endpointFor(Set.of(TENANT_B, TENANT_A));
    when(service.get(TENANT_A)).thenReturn(configuration(1, 50));
    when(service.get(TENANT_B)).thenReturn(configuration(1, 90));

    // when
    final var response = endpoint.get(null);

    // then - partition 1 exists in both tenants, so the response has to distinguish them; tenants
    // are reported in a stable order so that two reads of an unchanged cluster are diffable
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(bodyAsMap(response))
        .containsExactly(
            entry(TENANT_A, Map.of(1, limit(50))), entry(TENANT_B, Map.of(1, limit(90))));
  }

  @Test
  void shouldFilterReadToRequestedPhysicalTenant() {
    // given
    final var endpoint = endpointFor(Set.of(TENANT_A, TENANT_B));
    when(service.get(TENANT_B)).thenReturn(configuration(1, 90));

    // when
    final var response = endpoint.get(TENANT_B);

    // then - scoping to one tenant makes the partition id unambiguous again, so the flat shape is
    // returned instead of a single-entry nesting
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(Map.of(1, limit(90)));
    verify(service, never()).get(TENANT_A);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void shouldTreatMissingPhysicalTenantAsEveryPhysicalTenant(final String physicalTenant) {
    // given
    final var endpoint = endpointFor(Set.of(TENANT_A, TENANT_B));
    when(service.get(TENANT_A)).thenReturn(configuration(1, 50));
    when(service.get(TENANT_B)).thenReturn(configuration(1, 90));

    // when
    final var response = endpoint.get(physicalTenant);

    // then
    assertThat(bodyAsMap(response)).containsOnlyKeys(TENANT_A, TENANT_B);
  }

  @Test
  void shouldRejectReadOfUnknownPhysicalTenant() {
    // given
    final var endpoint = endpointFor(Set.of(DEFAULT_PHYSICAL_TENANT_ID));

    // when
    final var response = endpoint.get("nope");

    // then - reporting an empty configuration would read as "this tenant has no partitions" rather
    // than "there is no such tenant"
    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(bodyAsMap(response))
        .containsEntry("error", "Physical tenant 'nope' does not exist")
        .containsEntry("knownPhysicalTenants", Set.of(DEFAULT_PHYSICAL_TENANT_ID));
    verify(service, never()).get(any());
  }

  @Test
  void shouldApplyConfigurationToEveryPhysicalTenant() {
    // given
    final var endpoint = endpointFor(Set.of(TENANT_A, TENANT_B));
    final var flowControlCfg = new FlowControlCfg();
    when(service.set(flowControlCfg, TENANT_A)).thenReturn(configuration(1, 50));
    when(service.set(flowControlCfg, TENANT_B)).thenReturn(configuration(1, 50));

    // when
    final var response = endpoint.post(flowControlCfg, null);

    // then - a write without a tenant keeps its whole-cluster meaning
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(bodyAsMap(response)).containsOnlyKeys(TENANT_A, TENANT_B);
    verify(service).set(flowControlCfg, TENANT_A);
    verify(service).set(flowControlCfg, TENANT_B);
  }

  @Test
  void shouldScopeConfigurationToRequestedPhysicalTenant() {
    // given
    final var endpoint = endpointFor(Set.of(TENANT_A, TENANT_B));
    final var flowControlCfg = new FlowControlCfg();
    when(service.set(flowControlCfg, TENANT_A)).thenReturn(configuration(1, 50));

    // when
    final var response = endpoint.post(flowControlCfg, TENANT_A);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(Map.of(1, limit(50)));
    verify(service, never()).set(flowControlCfg, TENANT_B);
  }

  @Test
  void shouldRejectWriteToUnknownPhysicalTenant() {
    // given
    final var endpoint = endpointFor(Set.of(DEFAULT_PHYSICAL_TENANT_ID));
    final var flowControlCfg = new FlowControlCfg();

    // when
    final var response = endpoint.post(flowControlCfg, "nope");

    // then - the write must not silently fall back to another tenant
    assertThat(response.getStatusCode().value()).isEqualTo(404);
    verify(service, never()).set(any(), any());
  }

  @Test
  void shouldReportInternalErrorWhenPhysicalTenantIsUnreachable() {
    // given
    final var endpoint = endpointFor(Set.of(TENANT_A, TENANT_B));
    when(service.get(TENANT_A)).thenReturn(configuration(1, 50));
    when(service.get(TENANT_B))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("no leader")));

    // when
    final var response = endpoint.get(null);

    // then - a partially reachable cluster fails the read rather than under-reporting it
    assertThat(response.getStatusCode().value()).isEqualTo(500);
  }

  private FlowControlEndpoint endpointFor(final Set<String> knownPhysicalTenants) {
    final PhysicalTenantIds physicalTenantIds = () -> knownPhysicalTenants;
    return new FlowControlEndpoint(service, physicalTenantIds);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> bodyAsMap(final ResponseEntity<?> response) {
    return (Map<String, Object>) response.getBody();
  }

  private static CompletableFuture<Map<Integer, JsonNode>> configuration(
      final int partitionId, final int limit) {
    return CompletableFuture.completedFuture(Map.of(partitionId, limit(limit)));
  }

  private static JsonNode limit(final int limit) {
    return JsonNodeFactory.instance.objectNode().put("limit", limit);
  }
}

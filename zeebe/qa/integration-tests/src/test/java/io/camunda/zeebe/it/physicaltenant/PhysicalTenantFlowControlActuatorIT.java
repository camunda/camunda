/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import feign.FeignException;
import io.camunda.zeebe.qa.util.actuator.FlowControlActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the {@code flowControl} actuator distinguishes the partitions of different physical
 * tenants instead of aliasing them by numeric partition id, and that its {@code physicalTenant}
 * query parameter scopes both the read and the write to one tenant (ADR 003 D3).
 *
 * <p>Every tenant here owns a partition 1, which is exactly the case the pre-existing, flat
 * partition-keyed response could not represent.
 */
@ZeebeIntegration
final class PhysicalTenantFlowControlActuatorIT {

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_A = "tenanta";
  private static final int PARTITION_ID = 1;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  private final FlowControlActuator actuator = FlowControlActuator.of(broker);

  @Test
  void shouldReportEveryPhysicalTenantWithoutParameter() {
    // given
    awaitFlowControlReadable();

    // when - reading without a physicalTenant parameter on a node serving more than one tenant
    final var configurationByTenant = actuator.getFlowControlConfigurationByPhysicalTenant();

    // then - both tenants are reported under their own key; a flat partition-keyed map could only
    // have held one of the two partition 1s
    assertThat(configurationByTenant).containsOnlyKeys(DEFAULT_TENANT, TENANT_A);
    assertThat(configurationByTenant.get(DEFAULT_TENANT)).containsKey(PARTITION_ID);
    assertThat(configurationByTenant.get(TENANT_A)).containsKey(PARTITION_ID);
  }

  @Test
  void shouldScopeConfigurationToTargetedPhysicalTenant() {
    // given - each tenant is given a write rate limit of its own, so neither assertion depends on
    // what the default configuration happens to be
    awaitFlowControlReadable();
    actuator.setFlowControlConfiguration(writeRateLimitOf(111), TENANT_A);
    actuator.setFlowControlConfiguration(writeRateLimitOf(222), DEFAULT_TENANT);

    // when
    final var tenantAConfiguration = actuator.getFlowControlConfiguration(TENANT_A);
    final var defaultConfiguration = actuator.getFlowControlConfiguration(DEFAULT_TENANT);

    // then - each tenant kept its own limit, so neither write leaked into the other's partition 1
    assertThat(writeRateLimitIn(tenantAConfiguration)).isEqualTo(111);
    assertThat(writeRateLimitIn(defaultConfiguration)).isEqualTo(222);

    // and - the unscoped read reports the same two, distinguished by tenant
    final var configurationByTenant = actuator.getFlowControlConfigurationByPhysicalTenant();
    assertThat(writeRateLimitIn(configurationByTenant.get(TENANT_A))).isEqualTo(111);
    assertThat(writeRateLimitIn(configurationByTenant.get(DEFAULT_TENANT))).isEqualTo(222);
  }

  @Test
  void shouldApplyConfigurationToEveryPhysicalTenantWithoutParameter() {
    // given - the tenants start out with limits of their own, so a write that reaches only one of
    // them cannot be mistaken for one that reached both
    awaitFlowControlReadable();
    actuator.setFlowControlConfiguration(writeRateLimitOf(111), TENANT_A);
    actuator.setFlowControlConfiguration(writeRateLimitOf(222), DEFAULT_TENANT);

    // when - writing without a physicalTenant parameter
    final var response =
        actuator.setFlowControlConfigurationByPhysicalTenant(writeRateLimitOf(333));

    // then - the write kept its whole-cluster meaning, and the response reports the result per
    // tenant rather than aliasing the two partition 1s
    assertThat(response).containsOnlyKeys(DEFAULT_TENANT, TENANT_A);
    assertThat(writeRateLimitIn(response.get(TENANT_A))).isEqualTo(333);
    assertThat(writeRateLimitIn(response.get(DEFAULT_TENANT))).isEqualTo(333);

    // and - a subsequent read agrees, so the response was not merely echoing the request
    assertThat(writeRateLimitIn(actuator.getFlowControlConfiguration(TENANT_A))).isEqualTo(333);
    assertThat(writeRateLimitIn(actuator.getFlowControlConfiguration(DEFAULT_TENANT)))
        .isEqualTo(333);
  }

  @Test
  void shouldRejectUnknownPhysicalTenant() {
    // given
    awaitFlowControlReadable();

    // when / then - an unconfigured tenant is not an empty configuration
    assertThatThrownBy(() -> actuator.getFlowControlConfiguration("nosuchtenant"))
        .isInstanceOf(FeignException.NotFound.class)
        .hasMessageContaining("Physical tenant 'nosuchtenant' does not exist");
  }

  // a partition's flow control is only readable once the partition has a leader, which happens
  // asynchronously after startup for each tenant's partition group
  private void awaitFlowControlReadable() {
    await("every physical tenant has a leader for its partition")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(actuator.getFlowControlConfigurationByPhysicalTenant())
                    .containsOnlyKeys(DEFAULT_TENANT, TENANT_A));
  }

  private static int writeRateLimitIn(final Map<Integer, JsonNode> configuration) {
    return configuration.get(PARTITION_ID).get("writeRateLimit").get("limit").asInt();
  }

  private static String writeRateLimitOf(final int limit) {
    // language=JSON
    return """
        {
          "write": {
            "enabled": true,
            "rampUp": 0,
            "limit": %d
          }
        }"""
        .formatted(limit);
  }
}

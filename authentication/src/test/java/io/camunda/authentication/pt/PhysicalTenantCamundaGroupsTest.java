/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantCamundaGroupsTest {

  private static final String ROOT = "camunda.security.authentication";
  private static final String TENANT_A = "camunda.physical-tenants.tenanta.security.authentication";
  private static final String TENANT_B = "camunda.physical-tenants.tenantb.security.authentication";

  @Test
  void shouldUseTheGroupsModeOfEachTenant() {
    // given — the cluster has no groups claim, tenant a sets one, tenant b sets nothing
    final var environment =
        new MockEnvironment()
            .withProperty(ROOT + ".method", "oidc")
            .withProperty(TENANT_A + ".oidc.groups-claim", "groups")
            .withProperty(TENANT_B + ".oidc.client-id", "tenantb");
    final var camundaGroups = new PhysicalTenantCamundaGroups(environment);
    final var clusterConfig = oidc(null);

    // when / then
    assertThat(camundaGroups.isCamundaGroupsEnabled("tenanta", clusterConfig)).isFalse();
    assertThat(camundaGroups.isCamundaGroupsEnabled("tenantb", clusterConfig)).isTrue();
  }

  @Test
  void shouldUseTheClusterConfigForTheDefaultAndUnknownTenants() {
    // given — the environment would give camunda groups, the cluster config sets a groups claim
    final var environment = new MockEnvironment().withProperty(ROOT + ".method", "oidc");
    final var camundaGroups = new PhysicalTenantCamundaGroups(environment);
    final var clusterConfig = oidc("groups");

    // when / then
    assertThat(camundaGroups.isCamundaGroupsEnabled("default", clusterConfig)).isFalse();
    assertThat(camundaGroups.isCamundaGroupsEnabled("unknown", clusterConfig)).isFalse();
    assertThat(camundaGroups.isCamundaGroupsEnabled(null, clusterConfig)).isFalse();
  }

  @Test
  void shouldNotEnableCamundaGroupsWithoutAClusterConfig() {
    // given
    final var camundaGroups = new PhysicalTenantCamundaGroups(new MockEnvironment());

    // when / then
    assertThat(camundaGroups.isCamundaGroupsEnabled("default", null)).isFalse();
  }

  private static AuthenticationConfiguration oidc(final String groupsClaim) {
    final var oidc = new OidcConfiguration();
    oidc.setGroupsClaim(groupsClaim);
    final var config = new AuthenticationConfiguration();
    config.setMethod(AuthenticationMethod.OIDC);
    config.setOidc(oidc);
    return config;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beans.GatewayBasedProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  GatewayBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("gateway")
public class GatewayClusterIdPropertiesTest {

  @Nested
  class WithNoPropertiesSet {
    final GatewayBasedProperties gatewayCfg;

    WithNoPropertiesSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldUseDefaultMemberId() {
      assertThat(gatewayCfg.getCluster().getMemberId()).isEqualTo(DEFAULT_CLUSTER_MEMBER_ID);
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.cluster.member-id=my-gateway"})
  class WithMemberIdSet {
    final GatewayBasedProperties gatewayCfg;

    WithMemberIdSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetMemberId() {
      assertThat(gatewayCfg.getCluster().getMemberId()).isEqualTo("my-gateway");
    }
  }

  @Nested
  @TestPropertySource(properties = {"zeebe.gateway.cluster.memberId=legacy-gateway"})
  class WithLegacyGatewayMemberIdSet {
    final GatewayBasedProperties gatewayCfg;

    WithLegacyGatewayMemberIdSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetMemberIdFromLegacyGateway() {
      assertThat(gatewayCfg.getCluster().getMemberId()).isEqualTo("legacy-gateway");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.member-id=unified-gateway",
        "zeebe.gateway.cluster.memberId=legacy-gateway",
      })
  class WithNewAndLegacyMemberIdSet {
    final GatewayBasedProperties gatewayCfg;

    WithNewAndLegacyMemberIdSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldPrioritizeNewMemberId() {
      assertThat(gatewayCfg.getCluster().getMemberId()).isEqualTo("unified-gateway");
    }
  }
}

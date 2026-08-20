/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.zeebe.gateway.GatewayModuleConfiguration;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.function.Consumer;

/** Encapsulates an instance of the {@link GatewayModuleConfiguration} Spring application. */
public final class TestStandaloneGateway extends TestSpringApplication<TestStandaloneGateway>
    implements TestGateway<TestStandaloneGateway> {

  public TestStandaloneGateway() {
    super(GatewayModuleConfiguration.class, CommonsModuleConfiguration.class);
    // this is needed to ensure no default spring boot 4.0 security setup kicks in
    withAdditionalProfile(Profile.CONSOLIDATED_AUTH);

    unifiedConfig.getApi().getGrpc().setAddress("0.0.0.0");
    unifiedConfig.getApi().getGrpc().setPort(SocketUtil.getNextAddress().getPort());
    unifiedConfig
        .getCluster()
        .getNetwork()
        .getInternalApi()
        .setPort(SocketUtil.getNextAddress().getPort());
    withAdditionalProfile(Profile.GATEWAY);

    unifiedConfig.getSecurity().getAuthentication().setUnprotectedApi(true);
    unifiedConfig.getSecurity().getAuthorizations().setEnabled(false);

    // by default, we don't want to create the schema as ES/OS containers may not be used in the
    // current test
    withCreateSchema(false);
  }

  @Override
  public TestStandaloneGateway self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    // Gateway member ID via property
    return MemberId.from(unifiedConfig.getCluster().getGatewayId());
  }

  @Override
  public String host() {
    return unifiedConfig.getApi().getGrpc().getAddress();
  }

  @Override
  public boolean isGateway() {
    return true;
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case GATEWAY -> unifiedConfig.getApi().getGrpc().getPort();
      case CLUSTER -> unifiedConfig.getCluster().getNetwork().getInternalApi().getPort();
      default -> super.mappedPort(port);
    };
  }

  /**
   * Convenience method to modify cluster configuration using the unified configuration API.
   *
   * @param modifier a configuration function for cluster settings
   * @return itself for chaining
   */
  public TestStandaloneGateway withClusterConfig(
      final Consumer<io.camunda.configuration.Cluster> modifier) {
    modifier.accept(unifiedConfig.getCluster());
    return this;
  }
}

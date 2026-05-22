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
import io.camunda.configuration.Camunda;
import io.camunda.container.ExtendedConfigurationBuilder;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.zeebe.gateway.GatewayModuleConfiguration;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.function.Consumer;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Encapsulates an instance of the {@link GatewayModuleConfiguration} Spring application. */
public final class TestStandaloneGateway extends TestSpringApplication<TestStandaloneGateway>
    implements TestGateway<TestStandaloneGateway> {
  private final Camunda unifiedConfig;
  private final CamundaSecurityLibraryProperties securityConfig;

  public TestStandaloneGateway() {
    super(GatewayModuleConfiguration.class, CommonsModuleConfiguration.class);
    // this is needed to ensure no default spring boot 4.0 security setup kicks in
    withAdditionalProfile(Profile.CONSOLIDATED_AUTH);

    unifiedConfig = new Camunda();

    unifiedConfig.getApi().getGrpc().setAddress("0.0.0.0");
    unifiedConfig.getApi().getGrpc().setPort(SocketUtil.getNextAddress().getPort());
    unifiedConfig
        .getCluster()
        .getNetwork()
        .getInternalApi()
        .setPort(SocketUtil.getNextAddress().getPort());
    withAdditionalProfile(Profile.GATEWAY);

    securityConfig = new CamundaSecurityLibraryProperties();
    securityConfig.getAuthentication().setUnprotectedApi(true);
    securityConfig.getAuthorizations().setEnabled(false);
    //noinspection resource
    withBean("securityConfig", securityConfig, CamundaSecurityLibraryProperties.class);

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

  /**
   * Modifies the unified configuration (camunda.* properties).
   *
   * @param modifier a configuration function that accepts the Camunda configuration object
   * @return itself for chaining
   */
  @Override
  public TestStandaloneGateway withUnifiedConfig(final Consumer<Camunda> modifier) {
    modifier.accept(unifiedConfig);
    return this;
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case GATEWAY -> unifiedConfig.getApi().getGrpc().getPort();
      case CLUSTER -> unifiedConfig.getCluster().getNetwork().getInternalApi().getPort();
      default -> super.mappedPort(port);
    };
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    // Flatten the in-memory unified config into camunda.* properties at the latest possible point,
    // so every withClusterConfig/withUnifiedConfig/... call made up to now is captured. Refreshable
    // so that fields cleared between stop/start don't remain.
    withRefreshableProperties(ExtendedConfigurationBuilder.flatPropertiesFor(unifiedConfig));
    return super.createSpringBuilder();
  }

  /**
   * Returns the unified configuration object. This provides access to the camunda.* configuration
   * structure.
   *
   * @return the Camunda unified configuration object
   */
  @Override
  public Camunda unifiedConfig() {
    return unifiedConfig;
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

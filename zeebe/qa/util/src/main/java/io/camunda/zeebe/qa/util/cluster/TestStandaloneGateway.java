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
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.ActorClockControlledPropertiesOverride;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.IdleStrategyPropertiesOverride;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.zeebe.gateway.GatewayModuleConfiguration;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.function.Consumer;

/** Encapsulates an instance of the {@link GatewayModuleConfiguration} Spring application. */
public final class TestStandaloneGateway extends TestSpringApplication<TestStandaloneGateway>
    implements TestGateway<TestStandaloneGateway> {
  private final GatewayBasedProperties config;
  private final CamundaSecurityProperties securityConfig;

  public TestStandaloneGateway() {
    super(
        GatewayModuleConfiguration.class,
        CommonsModuleConfiguration.class,
        UnifiedConfiguration.class,
        UnifiedConfigurationHelper.class,
        TasklistPropertiesOverride.class,
        OperatePropertiesOverride.class,
        BrokerBasedPropertiesOverride.class,
        ActorClockControlledPropertiesOverride.class,
        IdleStrategyPropertiesOverride.class);
    config = new GatewayBasedProperties();

    config.getNetwork().setHost("0.0.0.0");
    config.getNetwork().setPort(SocketUtil.getNextAddress().getPort());
    config.getCluster().setPort(SocketUtil.getNextAddress().getPort());

    //noinspection resource
    withBean("config", config, GatewayBasedProperties.class).withAdditionalProfile(Profile.GATEWAY);

    securityConfig = new CamundaSecurityProperties();
    securityConfig.getAuthentication().setUnprotectedApi(true);
    securityConfig.getAuthorizations().setEnabled(false);
    //noinspection resource
    withBean("securityConfig", securityConfig, CamundaSecurityProperties.class);

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
    return MemberId.from(config.getCluster().getMemberId());
  }

  @Override
  public String host() {
    return config.getNetwork().getHost();
  }

  @Override
  public boolean isGateway() {
    return true;
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case GATEWAY -> config.getNetwork().getPort();
      case CLUSTER -> config.getCluster().getPort();
      default -> super.mappedPort(port);
    };
  }

  @Override
  public TestStandaloneGateway withGatewayConfig(final Consumer<GatewayCfg> modifier) {
    modifier.accept(config);
    return self();
  }

  @Override
  public GatewayCfg gatewayConfig() {
    return config;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.netty.util.NetUtil;
import java.util.Collections;

public final class EmbeddedGatewayCfg extends GatewayCfg implements ConfigurationEntry {

  private boolean enable = true;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {

    // configure gateway based on broker network settings
    final NetworkCfg networkCfg = globalConfig.getNetwork();

    init(networkCfg.getHost());

    // ensure embedded gateway can access local broker
    getCluster()
        .setInitialContactPoints(
            Collections.singletonList(
                NetUtil.toSocketAddressString(networkCfg.getInternalApi().getAddress())));

    // configure embedded gateway based on broker config
    getNetwork().setPort(getNetwork().getPort() + (networkCfg.getPortOffset() * 10));
  }

  public boolean isEnable() {
    return enable;
  }

  public EmbeddedGatewayCfg setEnable(final boolean enable) {
    this.enable = enable;
    return this;
  }
}

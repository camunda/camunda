/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration.legacy;

import static io.zeebe.broker.system.configuration.legacy.EnvironmentConstants.ENV_EMBED_GATEWAY;

import io.zeebe.gateway.impl.configuration.legacy.GatewayCfg;
import io.zeebe.util.Environment;

@Deprecated(since = "0.23.0-alpha1")
/* Kept in order to be able to offer a migration path for old configuration.
 * It is not yet clear whether we intent to offer a migration path for old configurations.
 * This class might be moved or removed on short notice.
 */
public final class EmbeddedGatewayCfg extends GatewayCfg implements ConfigurationEntry {

  private boolean enable = true;

  @Override
  public void init(
      final BrokerCfg globalConfig, final String brokerBase, final Environment environment) {
    environment.getBool(ENV_EMBED_GATEWAY).ifPresent(this::setEnable);

    // configure gateway based on broker network settings
    final NetworkCfg networkCfg = globalConfig.getNetwork();

    // network host precedence from higher to lower:
    // 1. ENV_GATEWAY_HOST
    // 2. specified in gateway toml section
    // 3. ENV_HOST
    // 4. specified in broker network toml section
    init(environment, networkCfg.getHost());

    // ensure embedded gateway can access local broker
    getCluster().setContactPoint(networkCfg.getInternalApi().getAddress().toString());

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

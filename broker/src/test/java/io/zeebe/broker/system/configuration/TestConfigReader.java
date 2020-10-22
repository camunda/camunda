/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.test.util.TestConfigurationFactory;
import io.zeebe.util.Environment;
import java.util.Map;

public final class TestConfigReader {

  private static final String BROKER_BASE = "test";

  public static BrokerCfg readConfig(final String name, final Map<String, String> environment) {
    final String configPath = "/system/" + name + ".yaml";

    final Environment environmentVariables = new Environment(environment);

    final BrokerCfg config =
        new TestConfigurationFactory()
            .create(environmentVariables, "zeebe.broker", configPath, BrokerCfg.class);
    config.init(BROKER_BASE, environmentVariables);

    return config;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import picocli.CommandLine.Command;

@Command(
    name = "state",
    description = "State management commands",
    subcommands = {StateUpdateKeyCommand.class})
public class StateCommand {

  private final ZeebeDbFactory zeebeDbFactory;

  public StateCommand() {
    zeebeDbFactory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration().setWalDisabled(false),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);
  }

  public ZeebeDbFactory getZeebeDbFactory() {
    return zeebeDbFactory;
  }
}

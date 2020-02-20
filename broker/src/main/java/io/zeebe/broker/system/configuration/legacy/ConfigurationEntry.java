/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration.legacy;

import io.zeebe.util.Environment;

@Deprecated(since = "0.23.0-alpha1")
/* Kept in order to be able to offer a migration path for old configuration.
 * It is not yet clear whether we intent to offer a migration path for old configurations.
 * This class might be moved or removed on short notice.
 */
public interface ConfigurationEntry {
  default void init(
      final BrokerCfg globalConfig, final String brokerBase, final Environment environment) {
    // noop;
  }
}

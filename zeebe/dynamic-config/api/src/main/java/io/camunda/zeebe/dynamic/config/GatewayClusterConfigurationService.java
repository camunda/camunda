/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

/**
 * Minimal service required by the gateway: it only listens to cluster configuration changes and
 * relays them via gossip, it never makes changes to the configuration itself.
 */
public interface GatewayClusterConfigurationService
    extends ClusterConfigurationUpdateNotifier, AutoCloseable {

  @Override
  void close();
}

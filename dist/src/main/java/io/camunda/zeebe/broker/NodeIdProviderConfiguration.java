/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.InvalidConfigurationException;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile(value = {"broker", "restore"})
public class NodeIdProviderConfiguration {

  private final BrokerBasedProperties properties;

  @Autowired
  public NodeIdProviderConfiguration(final BrokerBasedProperties properties) {
    this.properties = properties;
  }

  @Bean
  public NodeIdProvider staticNodeIdProvider() {
    final var nodeId = properties.getCluster().getNodeId();
    if (nodeId == null) {
      // FIXME rephrase better
      throw new InvalidConfigurationException("Expecting nodeId to be set, but is null", null);
    }
    return NodeIdProvider.staticProvider(nodeId);
  }
}

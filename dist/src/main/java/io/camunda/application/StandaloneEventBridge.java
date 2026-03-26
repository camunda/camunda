/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.eventbridge.EventBridgeGatewayConfiguration;
import org.springframework.boot.SpringBootConfiguration;

/**
 * Entry point for the standalone Event Bridge component.
 *
 * <p>Starts a single JVM containing both the HTTP gateway and all configured broker partitions.
 * Suitable for single-node development and testing. For multi-node cluster deployments, run one
 * instance per physical node with a distinct {@code event-bridge.node-id} and {@code
 * event-bridge.advertised-host} configuration.
 *
 * <p>Usage: {@code bin/event-bridge [--spring.config.location=...]}
 */
@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneEventBridge {

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/event_bridge_banner.txt");

    MainSupport.createDefaultApplicationBuilder()
        .sources(EventBridgeModuleConfiguration.class, EventBridgeGatewayConfiguration.class)
        .profiles(Profile.EVENT_BRIDGE.getId(), Profile.STANDALONE.getId())
        .build(args)
        .run();
  }
}

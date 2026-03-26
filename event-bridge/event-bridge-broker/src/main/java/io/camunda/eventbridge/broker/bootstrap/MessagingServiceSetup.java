/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and starts the broker's command API messaging service. This is the endpoint that gateways
 * connect to for publish and coordination requests (port 26501 by default).
 *
 * <p>Separate from the cluster messaging service (port 26502) used for SWIM gossip and raft
 * replication.
 */
final class MessagingServiceSetup {

  private static final Logger LOG = LoggerFactory.getLogger(MessagingServiceSetup.class);

  private final EventBridgeProperties properties;
  private final MeterRegistry meterRegistry;

  private NettyMessagingService messagingService;

  MessagingServiceSetup(final EventBridgeProperties properties, final MeterRegistry meterRegistry) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;
  }

  MessagingService start() {
    final var clusterCfg = properties.cluster();
    final var address = Address.from(Address.defaultAdvertisedHost().getHostAddress(), 26501);

    messagingService =
        new NettyMessagingService(
            "event-bridge",
            address,
            new io.atomix.cluster.messaging.MessagingConfig(),
            "EB-" + clusterCfg.nodeId(),
            meterRegistry);

    messagingService.start().join();

    LOG.info("Broker messaging service started on {}", address);
    return messagingService;
  }

  void stop() {
    if (messagingService != null) {
      try {
        messagingService.stop().join();
      } catch (final Exception e) {
        LOG.warn("Error stopping broker messaging service", e);
      }
      messagingService = null;
      LOG.info("Broker messaging service stopped");
    }
  }
}

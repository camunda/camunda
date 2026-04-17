/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.grpc.Status.Code;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared connection-readiness component used by both Starter and Worker. Retrieves the topology
 * (retrying until it succeeds) and publishes the {@code app.connected} gauge that the load-test
 * verification workflow monitors.
 */
@Component
public class ConnectionMonitor {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionMonitor.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOG, Duration.ofSeconds(5));

  private final ZeebeClient client;
  private final AtomicInteger connected = new AtomicInteger(0);

  public ConnectionMonitor(final ZeebeClient client, final MeterRegistry registry) {
    this.client = client;
    Gauge.builder(AppMetricsDoc.CONNECTED.getName(), connected, AtomicInteger::get)
        .description(AppMetricsDoc.CONNECTED.getDescription())
        .register(registry);
  }

  /**
   * Blocks until the topology request succeeds, logging each broker/partition once. Flips the
   * {@code app.connected} gauge to 1 on success. Fails fast on authentication errors.
   */
  public void awaitAndPrintTopology() {
    while (true) {
      try {
        final var topology = client.newTopologyRequest().send().join();
        topology
            .getBrokers()
            .forEach(
                b -> {
                  LOG.info("Broker {} - {} ({})", b.getNodeId(), b.getAddress(), b.getVersion());
                  b.getPartitions()
                      .forEach(p -> LOG.info("{} - {}", p.getPartitionId(), p.getRole()));
                });
        connected.set(1);
        return;
      } catch (final ClientStatusException e) {
        final var statusCode = e.getStatusCode();
        if (statusCode.equals(Code.UNAUTHENTICATED) || statusCode.equals(Code.PERMISSION_DENIED)) {
          LOG.error(
              "Failed to retrieve topology due to authentication error; check your config", e);
          System.exit(1);
        }
        THROTTLED_LOGGER.warn("Failed to retrieve topology due to client exception: ", e);
        sleep();
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Failed to retrieve topology: ", e);
        sleep();
      }
    }
  }

  private static void sleep() {
    try {
      Thread.sleep(1000);
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    }
  }
}

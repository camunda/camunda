/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Shared by the starter and the worker. On start it requests the cluster topology without blocking,
 * retries with exponential backoff and jitter until the request succeeds, then publishes {@link
 * Connected}. It publishes the {@code app.connected} gauge that the load-test verification workflow
 * monitors, and reports the readiness of the application.
 */
@Component("connectionReady")
public class ClusterConnection implements SmartLifecycle, HealthIndicator {

  static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
  static final Duration MAX_BACKOFF = Duration.ofSeconds(10);

  private static final Logger LOG = LoggerFactory.getLogger(ClusterConnection.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOG, Duration.ofSeconds(5));

  private final CamundaClient client;
  private final ApplicationEventPublisher eventPublisher;
  private final TaskScheduler scheduler;
  private final AtomicInteger connected = new AtomicInteger(0);
  private volatile boolean running;

  public ClusterConnection(
      final CamundaClient client,
      final MeterRegistry registry,
      final ApplicationEventPublisher eventPublisher,
      final TaskScheduler scheduler) {
    this.client = client;
    this.eventPublisher = eventPublisher;
    this.scheduler = scheduler;
    Gauge.builder(AppMetricsDoc.CONNECTED.getName(), connected, AtomicInteger::get)
        .description(AppMetricsDoc.CONNECTED.getDescription())
        .register(registry);
  }

  @Override
  public void start() {
    running = true;
  }

  /**
   * Runs after every lifecycle bean started. The annotated job workers register in a lifecycle
   * start of the Camunda client, and {@link Connected} listeners must find them registered.
   */
  @EventListener(ApplicationReadyEvent.class)
  void connect() {
    requestTopology(0);
  }

  @Override
  public void stop() {
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  public boolean isConnected() {
    return connected.get() == 1;
  }

  @Override
  public Health health() {
    return isConnected() ? Health.up().build() : Health.outOfService().build();
  }

  static Duration backoff(final int attempt) {
    final long cap = MAX_BACKOFF.toMillis();
    final long exponential = INITIAL_BACKOFF.toMillis() << Math.min(attempt, 10);
    final long delay = Math.min(cap, exponential);
    return Duration.ofMillis(ThreadLocalRandom.current().nextLong(delay / 2, delay + 1));
  }

  private void requestTopology(final int attempt) {
    if (!running) {
      return;
    }
    client
        .newTopologyRequest()
        .send()
        .whenComplete(
            (topology, error) -> {
              if (error == null) {
                topology
                    .getBrokers()
                    .forEach(
                        b -> {
                          LOG.info(
                              "Broker {} - {} ({})", b.getNodeId(), b.getAddress(), b.getVersion());
                          b.getPartitions()
                              .forEach(p -> LOG.info("{} - {}", p.getPartitionId(), p.getRole()));
                        });
                // The listeners are not idempotent, so a failure is not retried: the application
                // stays not ready and the startup probe restarts it.
                try {
                  eventPublisher.publishEvent(new Connected());
                } catch (final RuntimeException e) {
                  LOG.error("A Connected listener failed, the application stays not ready", e);
                  return;
                }
                connected.set(1);
              } else {
                THROTTLED_LOGGER.warn("Failed to retrieve topology: ", error);
                scheduleRetry(attempt + 1);
              }
            });
  }

  private void scheduleRetry(final int attempt) {
    if (!running) {
      return;
    }
    try {
      scheduler.schedule(() -> requestTopology(attempt), Instant.now().plus(backoff(attempt)));
    } catch (final TaskRejectedException e) {
      LOG.debug("Not retrying the topology request, the connection is stopping");
    }
  }
}

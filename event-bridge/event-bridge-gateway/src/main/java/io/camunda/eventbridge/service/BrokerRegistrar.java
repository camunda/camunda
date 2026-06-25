/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.service;

import io.camunda.eventbridge.broker.request.coordination.BrokerLivenessHeartbeatRequest;
import io.camunda.eventbridge.broker.request.coordination.BrokerRegisterRequest;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Registers this node's broker with the metadata-group leader and keeps its liveness session alive
 * — the broker side of the broker liveness FSM. On start it sends a {@code REGISTER_BROKER}
 * (retrying until the metadata leader is up and assigns an epoch), then heartbeats on a fixed
 * interval shorter than the leader's session timeout; a {@code FENCED} reply (stale epoch) triggers
 * re-registration. On graceful shutdown it heartbeats with {@code draining} set until the leader
 * has moved its replicas off and acknowledges shutdown.
 *
 * <p>Runs in the same process as the gateway and reuses its {@link BrokerClient}, which routes to
 * the metadata routing group's leader from gossip (with NOT_LEADER retry). Started last and stopped
 * first ({@link #getPhase()}) so it registers only once the cluster is up and drains before
 * teardown.
 */
@Component
public final class BrokerRegistrar implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerRegistrar.class);
  // Must be comfortably below the leader's BROKER_SESSION_TIMEOUT (10s).
  private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(3);
  private static final Duration REGISTER_RETRY = Duration.ofSeconds(1);
  private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(30);

  private final BrokerClient brokerClient;
  private final int brokerId;

  private volatile boolean running;
  private volatile boolean draining;
  private volatile long incarnation;
  private volatile long brokerEpoch = -1L;
  private CompletableFuture<Void> drained;
  private ScheduledExecutorService scheduler;

  public BrokerRegistrar(final BrokerClient brokerClient, final EventBridgeProperties properties) {
    this.brokerClient = brokerClient;
    brokerId = parseNodeId(properties.cluster().nodeId());
  }

  @Override
  public synchronized void start() {
    running = true;
    draining = false;
    drained = new CompletableFuture<>();
    incarnation = System.currentTimeMillis();
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, "broker-registrar-" + brokerId);
              thread.setDaemon(true);
              return thread;
            });
    register();
  }

  @Override
  public synchronized void stop() {
    // Graceful shutdown: ask the leader to drain our replicas, then wait for the ack (best-effort).
    draining = true;
    try {
      drained.get(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final Exception e) {
      LOG.warn(
          "Broker {} drain did not complete within {}; stopping anyway", brokerId, DRAIN_TIMEOUT);
    }
    running = false;
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    // Start after the cluster/broker bootstrap, stop before it.
    return Integer.MAX_VALUE - 1000;
  }

  private void register() {
    if (!running) {
      return;
    }
    brokerClient
        .sendRequest(new BrokerRegisterRequest().wrapRequest(brokerId, incarnation))
        .whenComplete(
            (response, error) -> {
              if (!running) {
                return;
              }
              if (error != null) {
                LOG.debug(
                    "Broker {} registration not accepted yet ({}); retrying",
                    brokerId,
                    error.getMessage());
                reschedule(this::register, REGISTER_RETRY);
                return;
              }
              brokerEpoch = response.getResponse().getBrokerEpoch();
              LOG.info(
                  "Broker {} registered with the metadata group at epoch {}",
                  brokerId,
                  brokerEpoch);
              reschedule(this::heartbeat, HEARTBEAT_INTERVAL);
            });
  }

  private void heartbeat() {
    if (!running) {
      return;
    }
    brokerClient
        .sendRequest(
            new BrokerLivenessHeartbeatRequest().wrapRequest(brokerId, brokerEpoch, draining))
        .whenComplete(
            (response, error) -> {
              if (!running) {
                return;
              }
              if (error != null) {
                // Transient (leader change / timeout) — retry on the next tick.
                reschedule(this::heartbeat, HEARTBEAT_INTERVAL);
                return;
              }
              final var reply = response.getResponse();
              if (reply.getErrorCode() == CoordinationErrorCode.FENCED_MEMBER_EPOCH) {
                LOG.info("Broker {} fenced by the metadata leader; re-registering", brokerId);
                register();
                return;
              }
              if (draining && reply.getShouldShutdown()) {
                LOG.info("Broker {} fully drained; shutdown acknowledged", brokerId);
                drained.complete(null);
                return;
              }
              reschedule(this::heartbeat, HEARTBEAT_INTERVAL);
            });
  }

  private void reschedule(final Runnable task, final Duration delay) {
    if (running && scheduler != null && !scheduler.isShutdown()) {
      scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  private static int parseNodeId(final String nodeId) {
    try {
      return Integer.parseInt(nodeId.replaceAll("[^0-9]", ""));
    } catch (final NumberFormatException e) {
      return 0;
    }
  }
}

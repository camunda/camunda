/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.crac;

import io.atomix.cluster.AtomixCluster;
import io.camunda.search.connect.tenant.SearchClients;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.crac.Context;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stops (and restarts) the Zeebe broker stack around a CRaC checkpoint, in explicit dependency
 * order. Registered with the global CRaC context by {@link CracCheckpointConfiguration}.
 *
 * <p><b>Why an {@code org.crac.Resource} and not {@code SmartLifecycle}:</b> Spring Boot's
 * checkpoint-on-refresh only invokes registered {@code org.crac.Resource} beans — it does not stop
 * arbitrary {@code SmartLifecycle} beans (verified on Spring Boot 4 / Spring Framework 7). Each
 * component restarts in place (scheduler/cluster) or recreates its actors on start (broker client /
 * broker), so cached consumer references stay valid across the cycle.
 *
 * <p>{@code beforeCheckpoint}: stop top-down — broker, broker client, search clients, cluster,
 * scheduler (scheduler last, since everything runs on it). {@code afterRestore}: start bottom-up.
 *
 * <p>Opt-in via {@code camunda.crac.enabled=true}.
 *
 * <p><b>Scope / known limitation (see {@code docs/crac/broker-restartability-design.md}):</b> this
 * cleanly tears down the broker stack, but a CRaC checkpoint of the unified application also
 * requires every other event loop in the process to be released — the webapp tier's Elasticsearch
 * clients, the JDK {@code HttpClient} selector managers, gRPC, and the servlet container. Those are
 * not owned by this resource, so on the unified app the checkpoint still aborts on ~40 residual
 * event-loop descriptors. The recommended path is Option A in the design doc (defer I/O startup
 * until after the checkpoint) rather than closing every loop here.
 */
@Component
@ConditionalOnProperty(prefix = "camunda.crac", name = "enabled", havingValue = "true")
public class CracBrokerStackResource implements Resource {

  private static final Logger LOG = LoggerFactory.getLogger(CracBrokerStackResource.class);

  private final ObjectProvider<Broker> broker;
  private final ObjectProvider<BrokerClient> brokerClient;
  private final ObjectProvider<SearchClients> searchClients;
  private final ObjectProvider<AtomixCluster> cluster;
  private final ObjectProvider<ActorScheduler> scheduler;

  public CracBrokerStackResource(
      final ObjectProvider<Broker> broker,
      final ObjectProvider<BrokerClient> brokerClient,
      final ObjectProvider<SearchClients> searchClients,
      final ObjectProvider<AtomixCluster> cluster,
      final ObjectProvider<ActorScheduler> scheduler) {
    this.broker = broker;
    this.brokerClient = brokerClient;
    this.searchClients = searchClients;
    this.cluster = cluster;
    this.scheduler = scheduler;
  }

  @Override
  public void beforeCheckpoint(final Context<? extends Resource> context) throws Exception {
    LOG.info("CRaC: stopping broker stack before checkpoint");
    final var b = broker.getIfAvailable();
    if (b != null && b.isRunning()) {
      b.close();
    }
    final var bc = brokerClient.getIfAvailable();
    if (bc != null) {
      bc.close();
    }
    final var sc = searchClients.getIfAvailable();
    if (sc != null) {
      sc.close();
    }
    final var c = cluster.getIfAvailable();
    if (c != null && c.isRunning()) {
      c.stop().join();
    }
    final var s = scheduler.getIfAvailable();
    if (s != null && s.isRunning()) {
      s.stop().get();
    }
  }

  @Override
  public void afterRestore(final Context<? extends Resource> context) throws Exception {
    LOG.info("CRaC: starting broker stack after restore");
    final var s = scheduler.getIfAvailable();
    if (s != null && !s.isRunning()) {
      s.start();
    }
    final var c = cluster.getIfAvailable();
    if (c != null && !c.isRunning()) {
      c.start().join();
    }
    final var bc = brokerClient.getIfAvailable();
    if (bc != null) {
      bc.start().forEach(ActorFuture::join);
    }
    final var b = broker.getIfAvailable();
    if (b != null && !b.isRunning()) {
      b.start().join();
    }
  }
}

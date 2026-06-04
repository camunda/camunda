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
import org.crac.Context;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Concrete CRaC {@link Resource} participants for the resources that hold open sockets / netty file
 * descriptors and therefore abort a checkpoint unless released first. Registered with the global
 * CRaC context by {@link CracCheckpointConfiguration}.
 *
 * <p>Opt-in via {@code camunda.crac.enabled=true}. Each participant resolves its target bean lazily
 * through an {@link ObjectProvider} so it is a no-op in app variants where the bean is absent (e.g.
 * a webapp-only distribution has no {@link AtomixCluster}).
 *
 * <p><b>Scope (foundation milestone):</b> {@code beforeCheckpoint} releases the file descriptors so
 * the checkpoint can complete. {@code afterRestore} re-acquisition is only partially wired — see
 * the per-participant notes — and is the tracked follow-up before this is production-ready.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "camunda.crac", name = "enabled", havingValue = "true")
public class CracParticipantsConfiguration {

  @Bean
  public Resource searchClientsCheckpointResource(final ObjectProvider<SearchClients> provider) {
    return new SearchClientsCheckpointResource(provider);
  }

  @Bean
  public Resource atomixClusterCheckpointResource(final ObjectProvider<AtomixCluster> provider) {
    return new AtomixClusterCheckpointResource(provider);
  }

  /** Closes the Elasticsearch/OpenSearch transports (HTTP connection-pool sockets). */
  static final class SearchClientsCheckpointResource implements Resource {

    private static final Logger LOG =
        LoggerFactory.getLogger(SearchClientsCheckpointResource.class);

    private final ObjectProvider<SearchClients> provider;

    SearchClientsCheckpointResource(final ObjectProvider<SearchClients> provider) {
      this.provider = provider;
    }

    @Override
    public void beforeCheckpoint(final Context<? extends Resource> context) throws Exception {
      final SearchClients searchClients = provider.getIfAvailable();
      if (searchClients != null) {
        LOG.info("CRaC: closing search-client transports before checkpoint");
        searchClients.close();
      }
    }

    @Override
    public void afterRestore(final Context<? extends Resource> context) {
      // TODO(crac): search clients are closed at checkpoint but not yet re-created on restore —
      // consumers hold direct references to the SearchClients bean, so re-acquisition needs a
      // swappable holder. Tracked as follow-up; restore is not yet functional for this resource.
      LOG.warn("CRaC: search-client transports were closed at checkpoint and are NOT re-opened");
    }
  }

  /**
   * Stops the {@link AtomixCluster} cluster transport, which owns the netty event-loop groups
   * (epoll/eventfd) and cluster sockets shared by the broker and gateway.
   */
  static final class AtomixClusterCheckpointResource implements Resource {

    private static final Logger LOG =
        LoggerFactory.getLogger(AtomixClusterCheckpointResource.class);

    private final ObjectProvider<AtomixCluster> provider;

    AtomixClusterCheckpointResource(final ObjectProvider<AtomixCluster> provider) {
      this.provider = provider;
    }

    @Override
    public void beforeCheckpoint(final Context<? extends Resource> context) {
      final AtomixCluster cluster = provider.getIfAvailable();
      if (cluster != null) {
        LOG.info("CRaC: stopping AtomixCluster transport before checkpoint");
        cluster.stop().join();
      }
    }

    @Override
    public void afterRestore(final Context<? extends Resource> context) {
      final AtomixCluster cluster = provider.getIfAvailable();
      if (cluster != null) {
        // Best-effort restart; AtomixCluster is not designed for in-process restart, so dependent
        // components (BrokerClient, gateway) may need re-wiring. Tracked as follow-up.
        LOG.info("CRaC: restarting AtomixCluster transport after restore (best-effort)");
        cluster.start().join();
      }
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster;

import io.atomix.cluster.discovery.NodeDiscoveryConfig;
import io.atomix.cluster.discovery.NodeDiscoveryEvent;
import io.atomix.cluster.discovery.NodeDiscoveryEventListener;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.utils.event.AbstractListenerManager;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;

public final class TestDiscoveryProvider
    extends AbstractListenerManager<NodeDiscoveryEvent, NodeDiscoveryEventListener>
    implements NodeDiscoveryProvider {
  private final Set<Node> nodes = new CopyOnWriteArraySet<>();

  @Override
  public Set<Node> getNodes() {
    return nodes;
  }

  @Override
  public CompletableFuture<Void> join(final BootstrapService bootstrap, final Node localNode) {
    if (nodes.add(localNode)) {
      listenerRegistry.process(new NodeDiscoveryEvent(NodeDiscoveryEvent.Type.JOIN, localNode));
    }

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> leave(final Node localNode) {
    if (nodes.remove(localNode)) {
      listenerRegistry.process(new NodeDiscoveryEvent(NodeDiscoveryEvent.Type.LEAVE, localNode));
    }

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public NodeDiscoveryConfig config() {
    return null;
  }
}

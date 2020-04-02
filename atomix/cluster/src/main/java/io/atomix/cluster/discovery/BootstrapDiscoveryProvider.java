/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.discovery;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.Node;
import io.atomix.cluster.NodeConfig;
import io.atomix.utils.event.AbstractListenerManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cluster membership provider that bootstraps membership from a pre-defined set of peers.
 *
 * <p>The bootstrap member provider takes a set of peer {@link
 * BootstrapDiscoveryConfig#setNodes(Collection) addresses} and uses them to join the cluster. Using
 * the {@link io.atomix.cluster.messaging.MessagingService}, each node sends a heartbeat to its
 * configured bootstrap peers. Peers respond to each heartbeat message with a list of all known
 * peers, thus propagating membership information using a gossip style protocol.
 *
 * <p>A phi accrual failure detector is used to detect failures and remove peers from the
 * configuration. In order to avoid flapping of membership following a {@link
 * io.atomix.cluster.ClusterMembershipEvent.Type#MEMBER_ADDED} event, the implementation attempts to
 * heartbeat all newly discovered peers before triggering a {@link
 * io.atomix.cluster.ClusterMembershipEvent.Type#MEMBER_REMOVED} event.
 */
public class BootstrapDiscoveryProvider
    extends AbstractListenerManager<NodeDiscoveryEvent, NodeDiscoveryEventListener>
    implements NodeDiscoveryProvider {

  public static final Type TYPE = new Type();
  private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapDiscoveryProvider.class);
  private final ImmutableSet<Node> bootstrapNodes;
  private final BootstrapDiscoveryConfig config;

  public BootstrapDiscoveryProvider(final Node... bootstrapNodes) {
    this(Arrays.asList(bootstrapNodes));
  }

  public BootstrapDiscoveryProvider(final Collection<Node> bootstrapNodes) {
    this(
        new BootstrapDiscoveryConfig()
            .setNodes(
                bootstrapNodes.stream()
                    .map(node -> new NodeConfig().setId(node.id()).setAddress(node.address()))
                    .collect(Collectors.toList())));
  }

  BootstrapDiscoveryProvider(final BootstrapDiscoveryConfig config) {
    this.config = checkNotNull(config);
    this.bootstrapNodes =
        ImmutableSet.copyOf(config.getNodes().stream().map(Node::new).collect(Collectors.toList()));
  }

  /**
   * Creates a new bootstrap provider builder.
   *
   * @return a new bootstrap provider builder
   */
  public static BootstrapDiscoveryBuilder builder() {
    return new BootstrapDiscoveryBuilder();
  }

  @Override
  public BootstrapDiscoveryConfig config() {
    return config;
  }

  @Override
  public Set<Node> getNodes() {
    return bootstrapNodes;
  }

  @Override
  public CompletableFuture<Void> join(final BootstrapService bootstrap, final Node localNode) {
    LOGGER.info("Joined");
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> leave(final Node localNode) {
    LOGGER.info("Left");
    return CompletableFuture.completedFuture(null);
  }

  /** Bootstrap member location provider type. */
  public static class Type implements NodeDiscoveryProvider.Type<BootstrapDiscoveryConfig> {
    private static final String NAME = "bootstrap";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public BootstrapDiscoveryConfig newConfig() {
      return new BootstrapDiscoveryConfig();
    }

    @Override
    public NodeDiscoveryProvider newProvider(final BootstrapDiscoveryConfig config) {
      return new BootstrapDiscoveryProvider(config);
    }
  }
}

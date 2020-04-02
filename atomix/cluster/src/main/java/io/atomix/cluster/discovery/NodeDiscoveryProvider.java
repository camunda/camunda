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

import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.Node;
import io.atomix.utils.ConfiguredType;
import io.atomix.utils.config.Configured;
import io.atomix.utils.event.ListenerService;
import io.atomix.utils.net.Address;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Cluster membership provider.
 *
 * <p>The membership provider is an SPI that the {@link ClusterMembershipService} uses to locate new
 * members joining the cluster. It provides a simple TCP {@link Address} for members which will be
 * used by the {@link ClusterMembershipService} to exchange higher level {@link Member} information.
 * Membership providers are responsible for providing an actively managed view of cluster
 * membership.
 *
 * @see BootstrapDiscoveryProvider
 * @see MulticastDiscoveryProvider
 */
public interface NodeDiscoveryProvider
    extends ListenerService<NodeDiscoveryEvent, NodeDiscoveryEventListener>,
        Configured<NodeDiscoveryConfig> {

  /**
   * Returns the set of active nodes.
   *
   * @return the set of active nodes
   */
  Set<Node> getNodes();

  /**
   * Joins the cluster.
   *
   * @param bootstrap the bootstrap service
   * @param localNode the local node info
   * @return a future to be completed once the join is complete
   */
  CompletableFuture<Void> join(BootstrapService bootstrap, Node localNode);

  /**
   * Leaves the cluster.
   *
   * @param localNode the local node info
   * @return a future to be completed once the leave is complete
   */
  CompletableFuture<Void> leave(Node localNode);

  /** Membership provider type. */
  interface Type<C extends NodeDiscoveryConfig> extends ConfiguredType<C> {

    /**
     * Creates a new instance of the provider.
     *
     * @param config the provider configuration
     * @return the provider instance
     */
    NodeDiscoveryProvider newProvider(C config);
  }
}

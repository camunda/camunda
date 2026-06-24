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

import io.atomix.cluster.NodeConfig;
import java.util.Collection;
import java.util.Collections;

/** Bootstrap discovery configuration. */
public class BootstrapDiscoveryConfig extends NodeDiscoveryConfig {
  private Collection<NodeConfig> nodes = Collections.emptySet();

  @Override
  public NodeDiscoveryProvider.Type getType() {
    return BootstrapDiscoveryProvider.TYPE;
  }

  /**
   * Returns the configured bootstrap nodes.
   *
   * @return the configured bootstrap nodes
   */
  public Collection<NodeConfig> getNodes() {
    return nodes;
  }

  /**
   * Sets the bootstrap nodes.
   *
   * @param nodes the bootstrap nodes
   * @return the bootstrap provider configuration
   */
  public BootstrapDiscoveryConfig setNodes(final Collection<NodeConfig> nodes) {
    this.nodes = nodes;
    return this;
  }
}

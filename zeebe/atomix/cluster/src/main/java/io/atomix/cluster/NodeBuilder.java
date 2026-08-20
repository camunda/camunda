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
package io.atomix.cluster;

import io.atomix.utils.Builder;
import io.atomix.utils.net.Address;

/** Node builder. */
public class NodeBuilder implements Builder<Node> {
  protected final NodeConfig config;

  protected NodeBuilder(final NodeConfig config) {
    this.config = config;
  }

  /**
   * Sets the node identifier.
   *
   * @param id the node identifier
   * @return the node builder
   */
  public NodeBuilder withId(final String id) {
    config.setId(id);
    return this;
  }

  /**
   * Sets the node identifier.
   *
   * @param id the node identifier
   * @return the node builder
   */
  public NodeBuilder withId(final NodeId id) {
    config.setId(id);
    return this;
  }

  /**
   * Sets the node host.
   *
   * @param host the node host
   * @return the node builder
   */
  public NodeBuilder withHost(final String host) {
    config.setHost(host);
    return this;
  }

  /**
   * Sets the node port.
   *
   * @param port the node port
   * @return the node builder
   */
  public NodeBuilder withPort(final int port) {
    config.setPort(port);
    return this;
  }

  /**
   * Sets the node address.
   *
   * @param address the node address
   * @return the node builder
   */
  public NodeBuilder withAddress(final Address address) {
    config.setAddress(address);
    return this;
  }

  @Override
  public Node build() {
    return new Node(config);
  }
}

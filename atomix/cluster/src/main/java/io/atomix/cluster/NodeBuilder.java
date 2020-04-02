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
   * @param address a host:port tuple
   * @return the node builder
   * @throws io.atomix.utils.net.MalformedAddressException if a valid {@link Address} cannot be
   *     constructed from the arguments
   * @deprecated since 3.1. Use {@link #withHost(String)} and/or {@link #withPort(int)} instead
   */
  @Deprecated
  public NodeBuilder withAddress(final String address) {
    return withAddress(Address.from(address));
  }

  /**
   * Sets the node host/port.
   *
   * @param host the host name
   * @param port the port number
   * @return the node builder
   * @throws io.atomix.utils.net.MalformedAddressException if a valid {@link Address} cannot be
   *     constructed from the arguments
   * @deprecated since 3.1. Use {@link #withHost(String)} and {@link #withPort(int)} instead
   */
  @Deprecated
  public NodeBuilder withAddress(final String host, final int port) {
    return withAddress(Address.from(host, port));
  }

  /**
   * Sets the node address using local host.
   *
   * @param port the port number
   * @return the node builder
   * @throws io.atomix.utils.net.MalformedAddressException if a valid {@link Address} cannot be
   *     constructed from the arguments
   * @deprecated since 3.1. Use {@link #withPort(int)} instead
   */
  @Deprecated
  public NodeBuilder withAddress(final int port) {
    return withAddress(Address.from(port));
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

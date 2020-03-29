/*
 * Copyright 2014-present Open Networking Foundation
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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.utils.config.Configured;
import io.atomix.utils.net.Address;
import java.util.Objects;

/** Represents a node. */
public class Node implements Configured<NodeConfig> {

  private final NodeId id;
  private final Address address;

  public Node(final NodeConfig config) {
    this.id = config.getId();
    this.address = checkNotNull(config.getAddress(), "address cannot be null");
  }

  protected Node(final NodeId id, final Address address) {
    this.id = checkNotNull(id, "id cannot be null");
    this.address = checkNotNull(address, "address cannot be null");
  }

  /**
   * Returns a new member builder with no ID.
   *
   * @return the member builder
   */
  public static NodeBuilder builder() {
    return new NodeBuilder(new NodeConfig());
  }

  /**
   * Returns the instance identifier.
   *
   * @return instance identifier
   */
  public NodeId id() {
    return id;
  }

  /**
   * Returns the node address.
   *
   * @return the node address
   */
  public Address address() {
    return address;
  }

  @Override
  public NodeConfig config() {
    return new NodeConfig().setId(id).setAddress(address);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, address);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof Node) {
      final Node member = (Node) object;
      return member.id().equals(id()) && member.address().equals(address());
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(Node.class)
        .add("id", id)
        .add("address", address)
        .omitNullValues()
        .toString();
  }
}

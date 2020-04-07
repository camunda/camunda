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

import io.atomix.cluster.Node;
import io.atomix.utils.event.AbstractEvent;

/** Node discovery event. */
public class NodeDiscoveryEvent extends AbstractEvent<NodeDiscoveryEvent.Type, Node> {

  public NodeDiscoveryEvent(final Type type, final Node subject) {
    super(type, subject);
  }

  public NodeDiscoveryEvent(final Type type, final Node subject, final long time) {
    super(type, subject, time);
  }

  /**
   * Returns the node.
   *
   * @return the node
   */
  public Node node() {
    return subject();
  }

  /** Node discovery event type. */
  public enum Type {
    /** Indicates that the node joined the cluster. */
    JOIN,

    /** Indicates that the node left the cluster. */
    LEAVE,
  }
}

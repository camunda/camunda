/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import java.util.List;

/**
 * What a node knows about the other nodes from gossip. Gossip stops at the region border, so it
 * only covers the node's own region.
 */
public interface ClusterView {

  /** The nodes of this node's region that gossip sees as alive, this node included. */
  List<String> liveNodesInMyZone();

  /** Tells whether gossip sees the node as alive. A node of another region is never seen. */
  default boolean isAlive(final String nodeId) {
    return liveNodesInMyZone().contains(nodeId);
  }
}

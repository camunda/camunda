/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.util.function.UnaryOperator;

public interface ClusterTopologyManager {

  ActorFuture<ClusterTopology> getClusterTopology();

  ActorFuture<ClusterTopology> updateClusterTopology(
      UnaryOperator<ClusterTopology> topologyUpdated);
}

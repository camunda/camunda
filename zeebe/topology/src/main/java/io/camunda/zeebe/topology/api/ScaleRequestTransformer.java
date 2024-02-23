/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator.TopologyChangeRequest;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ScaleRequestTransformer implements TopologyChangeRequest {

  private final Set<MemberId> members;
  private final ArrayList<TopologyChangeOperation> generatedOperations = new ArrayList<>();

  public ScaleRequestTransformer(final Set<MemberId> members) {
    this.members = members;
  }

  @Override
  public Either<Exception, List<TopologyChangeOperation>> operations(
      final ClusterTopology currentTopology) {
    generatedOperations.clear();

    // First add new members
    return new AddMembersTransformer(members)
        .operations(currentTopology)
        .map(this::addToOperations)
        // then reassign partitions
        .flatMap(
            ignore -> new PartitionReassignRequestTransformer(members).operations(currentTopology))
        .map(this::addToOperations)
        // then remove members that are not part of the new topology
        .flatMap(
            ignore -> {
              final var membersToRemove =
                  currentTopology.members().keySet().stream()
                      .filter(m -> !members.contains(m))
                      .collect(Collectors.toSet());
              return new RemoveMembersTransformer(membersToRemove).operations(currentTopology);
            })
        .map(this::addToOperations);
  }

  private ArrayList<TopologyChangeOperation> addToOperations(
      final List<TopologyChangeOperation> reassignOperations) {
    generatedOperations.addAll(reassignOperations);
    return generatedOperations;
  }
}

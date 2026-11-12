/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.util.Objects;

/**
 * Names the state of one partition. All replicas of the partition share this state.
 *
 * @param partitionGroup the Raft partition group, at most 255 characters
 * @param partitionId the partition's id within its group
 */
public record StateIdentity(String partitionGroup, int partitionId) {

  public StateIdentity {
    Objects.requireNonNull(partitionGroup, "must specify a partition group");
  }

  @Override
  public String toString() {
    return partitionGroup + "-" + partitionId;
  }
}

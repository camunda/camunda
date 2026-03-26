/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupMemberSession {

  private final MemberMetadata metadata;
  private long assignmentEpoch;
  private Set<Integer> assignedPartitions;
  private Instant lastHeartbeat;

  GroupMemberSession(final MemberMetadata metadata, final Instant initialHeartbeat) {
    this.metadata = metadata;
    lastHeartbeat = initialHeartbeat;
    assignedPartitions = Set.of();
  }

  public MemberMetadata getMetadata() {
    return metadata;
  }

  public long getAssignmentEpoch() {
    return assignmentEpoch;
  }

  public void confirmAssignment(
      final long assignmentEpoch, final List<Integer> assignedPartitions) {
    this.assignmentEpoch = assignmentEpoch;
    this.assignedPartitions = new HashSet<>(assignedPartitions);
  }

  public Set<Integer> getAssignedPartitions() {
    return assignedPartitions;
  }

  public Instant getLastHeartbeat() {
    return lastHeartbeat;
  }

  public void setLastHeartbeat(final Instant lastHeartbeat) {
    this.lastHeartbeat = lastHeartbeat;
  }

  public boolean isSessionExpired(final Instant deadline) {
    return lastHeartbeat.isBefore(deadline);
  }
}

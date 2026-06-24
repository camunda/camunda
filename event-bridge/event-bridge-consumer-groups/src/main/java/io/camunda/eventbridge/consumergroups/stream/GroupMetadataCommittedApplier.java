/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.stream.TypedEventApplier;

/**
 * Applies {@code GROUP_METADATA_COMMITTED} events to {@link DbGroupMetadataState} — the only place
 * that mutates consumer-group metadata. It runs identically on the leader (right after {@link
 * GroupMetadataProcessor} writes the event) and on followers (on replay), so every replica rebuilds
 * identical membership and a new leader restores the registry after failover.
 */
final class GroupMetadataCommittedApplier
    implements TypedEventApplier<CoordinatorIntent, GroupMetadataRecord> {

  private final DbGroupMetadataState groupMetadataState;

  GroupMetadataCommittedApplier(final DbGroupMetadataState groupMetadataState) {
    this.groupMetadataState = groupMetadataState;
  }

  @Override
  public void applyState(final long key, final GroupMetadataRecord value) {
    groupMetadataState.put(value.getGroupId(), value.getPayload());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.group;

import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.zeebe.db.ZeebeDb;
import java.util.List;

/**
 * The coordinator's off-actor read view of consumer-group state — the event-bridge counterpart of
 * the engine's {@code StateQueryService}. It does not touch column families itself: on first use it
 * builds a {@link DbConsumerGroupState} on its <em>own</em> {@link ZeebeDb} context and delegates,
 * so the coordinator reads committed state off the stream-processing actor without sharing the
 * processor's flyweights. (The async tasks read state directly instead — they each hold their own
 * {@link ConsumerGroupState} on a private context.)
 *
 * <p>Must be used from a single actor (the {@link
 * io.camunda.eventbridge.consumergroups.membership.ConsumerGroupCoordinator}); the backing state is
 * created lazily so its context and flyweights belong to that reader thread.
 */
public final class ConsumerGroupQueryService {

  private final ZeebeDb<EventBridgeColumnFamilies> zeebeDb;
  private ConsumerGroupState state;

  public ConsumerGroupQueryService(final ZeebeDb<EventBridgeColumnFamilies> zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  /** A group's snapshot (with its members), or {@code null} if the group does not exist. */
  public GroupSnapshot groupSnapshot(final String group) {
    return state().groupSnapshot(group);
  }

  /** Snapshots of every group — for the coordinator's describe/seed reads. */
  public List<GroupSnapshot> allGroups() {
    return state().allGroups();
  }

  private ConsumerGroupState state() {
    if (state == null) {
      state = new DbConsumerGroupState(zeebeDb, zeebeDb.createContext());
    }
    return state;
  }
}

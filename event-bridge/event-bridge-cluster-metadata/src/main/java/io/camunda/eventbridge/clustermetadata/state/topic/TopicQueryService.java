/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.topic;

import io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies;
import io.camunda.eventbridge.clustermetadata.state.immutable.TopicState;
import io.camunda.zeebe.db.ZeebeDb;
import java.util.Map;

/**
 * An off-actor read view of the topic registry — the event-bridge counterpart of the engine's
 * {@code StateQueryService}. It does not touch column families itself: on first use it builds a
 * {@link DbTopicState} on its <em>own</em> {@link ZeebeDb} context and delegates, so a reader reads
 * committed state off the stream-processing actor without sharing the processor's flyweights.
 *
 * <p>Must be used from a single actor (the metadata leader's {@code MetadataManager}, or each
 * broker's reconcile loop); the backing state is created lazily so its context and flyweights
 * belong to that reader thread.
 */
public final class TopicQueryService {

  private final ZeebeDb<MetadataColumnFamilies> zeebeDb;
  private TopicState state;

  public TopicQueryService(final ZeebeDb<MetadataColumnFamilies> zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  /** A pinned snapshot of every registered topic — for listing, reconcile and failover reads. */
  public Map<String, TopicMetadata> topicsSnapshot() {
    return state().topicsSnapshot();
  }

  /** A single topic's desired configuration, or {@code null} if it does not exist. */
  public TopicMetadata topic(final String name) {
    return state().get(name);
  }

  private TopicState state() {
    if (state == null) {
      state = new DbTopicState(zeebeDb, zeebeDb.createContext());
    }
    return state;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.broker;

import io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies;
import io.camunda.eventbridge.clustermetadata.state.immutable.BrokerState;
import io.camunda.zeebe.db.ZeebeDb;
import java.util.Map;

/**
 * An off-actor read view of the broker registry — the broker counterpart of {@code
 * TopicQueryService}. On first use it builds a {@link DbBrokerState} on its <em>own</em> {@link
 * ZeebeDb} context and delegates, so a reader (the leader's heartbeat handler) reads committed
 * registration/liveness state off the stream-processing actor without sharing its flyweights.
 *
 * <p>Must be used from a single actor; the backing state is created lazily so its context and
 * flyweights belong to that reader thread.
 */
public final class BrokerQueryService {

  private final ZeebeDb<MetadataColumnFamilies> zeebeDb;
  private BrokerState state;

  public BrokerQueryService(final ZeebeDb<MetadataColumnFamilies> zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  public BrokerMetadata broker(final int brokerId) {
    return state().get(brokerId);
  }

  public Map<Integer, BrokerMetadata> brokersSnapshot() {
    return state().brokersSnapshot();
  }

  private BrokerState state() {
    if (state == null) {
      state = new DbBrokerState(zeebeDb, zeebeDb.createContext());
    }
    return state;
  }
}

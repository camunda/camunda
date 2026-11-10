/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import java.util.Optional;

/**
 * Hides the leaders of other zones. This is for tests and for a proof of concept only.
 *
 * <p>In a real deployment gossip stops at the border between regions by itself. A test cluster runs
 * in one network, so every broker is in the gossip view. This filter makes a node behave as if the
 * brokers of other zones were out of reach.
 */
public final class ZoneFilteredLeaderView implements LeaderView {

  private final LeaderView delegate;
  private final String ownZone;

  /**
   * @param delegate the view to filter
   * @param ownZone the zone of this node. A leader without a zone or with another zone is hidden.
   */
  public ZoneFilteredLeaderView(final LeaderView delegate, final String ownZone) {
    this.delegate = delegate;
    this.ownZone = ownZone;
  }

  @Override
  public Optional<LeaderInfo> leaderOf(final int partitionId) {
    return delegate.leaderOf(partitionId).filter(leader -> ownZone.equals(leader.zone()));
  }
}

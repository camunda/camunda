/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

public class Membership {

  /**
   * Configure whether to broadcast member updates to all members. If set to false updates will be
   * gossiped among the members. If set to true the network traffic may increase but it reduce the
   * time to detect membership changes.
   */
  private boolean broadcastUpdates = false;

  /**
   * Configure whether to broadcast disputes to all members. If set to true the network traffic may
   * increase but it reduce the time to detect membership changes.
   */
  private boolean broadcastDisputes = true;

  /** Configure whether to notify a suspect node on state changes. */
  private boolean notifySuspect = false;

  /** Sets the interval at which the membership updates are sent to a random member. */
  private Duration gossipInterval = Duration.ofMillis(250);

  /** Sets the number of members to which membership updates are sent at each gossip interval. */
  private int gossipFanout = 2;

  /** Sets the interval at which to probe a random member. */
  private Duration probeInterval = Duration.ofMillis(1000);

  /** Sets the timeout for a probe response. */
  private Duration probeTimeout = Duration.ofMillis(100);

  /** Sets the number of probes failed before declaring a member is suspect. */
  private int suspectProbes = 3;

  /** Sets the timeout for a suspect member is declared dead. */
  private Duration failureTimeout = Duration.ofMillis(10_000);

  /**
   * Sets the interval at which this member synchronizes its membership information with a random
   * member.
   */
  private Duration syncInterval = Duration.ofMillis(10_000);

  public boolean isBroadcastUpdates() {
    return broadcastUpdates;
  }

  public void setBroadcastUpdates(final boolean broadcastUpdates) {
    this.broadcastUpdates = broadcastUpdates;
  }

  public boolean isBroadcastDisputes() {
    return broadcastDisputes;
  }

  public void setBroadcastDisputes(final boolean broadcastDisputes) {
    this.broadcastDisputes = broadcastDisputes;
  }

  public boolean isNotifySuspect() {
    return notifySuspect;
  }

  public void setNotifySuspect(final boolean notifySuspect) {
    this.notifySuspect = notifySuspect;
  }

  public Duration getGossipInterval() {
    return gossipInterval;
  }

  public void setGossipInterval(final Duration gossipInterval) {
    this.gossipInterval = gossipInterval;
  }

  public int getGossipFanout() {
    return gossipFanout;
  }

  public void setGossipFanout(final int gossipFanout) {
    this.gossipFanout = gossipFanout;
  }

  public Duration getProbeInterval() {
    return probeInterval;
  }

  public void setProbeInterval(final Duration probeInterval) {
    this.probeInterval = probeInterval;
  }

  public Duration getProbeTimeout() {
    return probeTimeout;
  }

  public void setProbeTimeout(final Duration probeTimeout) {
    this.probeTimeout = probeTimeout;
  }

  public int getSuspectProbes() {
    return suspectProbes;
  }

  public void setSuspectProbes(final int suspectProbes) {
    this.suspectProbes = suspectProbes;
  }

  public Duration getFailureTimeout() {
    return failureTimeout;
  }

  public void setFailureTimeout(final Duration failureTimeout) {
    this.failureTimeout = failureTimeout;
  }

  public Duration getSyncInterval() {
    return syncInterval;
  }

  public void setSyncInterval(final Duration syncInterval) {
    this.syncInterval = syncInterval;
  }

  @Override
  public Membership clone() {
    final Membership copy = new Membership();
    copy.broadcastUpdates = broadcastUpdates;
    copy.broadcastDisputes = broadcastDisputes;
    copy.notifySuspect = notifySuspect;
    copy.gossipInterval = gossipInterval;
    copy.gossipFanout = gossipFanout;
    copy.probeInterval = probeInterval;
    copy.probeTimeout = probeTimeout;
    copy.suspectProbes = suspectProbes;
    copy.failureTimeout = failureTimeout;
    copy.syncInterval = syncInterval;

    return copy;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.time.Duration;

public final class MembershipCfg {
  // the following values are from atomix per default
  private static final boolean DEFAULT_BROADCAST_UPDATES = false;
  private static final boolean DEFAULT_BROADCAST_DISPUTES = true;
  private static final boolean DEFAULT_NOTIFY_SUSPECT = false;
  private static final Duration DEFAULT_GOSSIP_INTERVAL = Duration.ofMillis(250);
  private static final int DEFAULT_GOSSIP_FANOUT = 2;
  private static final Duration DEFAULT_PROBE_INTERVAL = Duration.ofMillis(1000);
  private static final Duration DEFAULT_PROBE_TIMEOUT = Duration.ofMillis(100);
  private static final int DEFAULT_SUSPECT_PROBES = 3;
  private static final Duration DEFAULT_FAILURE_TIMEOUT = Duration.ofMillis(10_000);
  private static final Duration DEFAULT_SYNC_INTERVAL = Duration.ofMillis(10_000);

  private boolean broadcastUpdates = DEFAULT_BROADCAST_UPDATES;
  private boolean broadcastDisputes = DEFAULT_BROADCAST_DISPUTES;
  private boolean notifySuspect = DEFAULT_NOTIFY_SUSPECT;
  private Duration gossipInterval = DEFAULT_GOSSIP_INTERVAL;
  private int gossipFanout = DEFAULT_GOSSIP_FANOUT;
  private Duration probeInterval = DEFAULT_PROBE_INTERVAL;
  private Duration probeTimeout = DEFAULT_PROBE_TIMEOUT;
  private int suspectProbes = DEFAULT_SUSPECT_PROBES;
  private Duration failureTimeout = DEFAULT_FAILURE_TIMEOUT;
  private Duration syncInterval = DEFAULT_SYNC_INTERVAL;

  public boolean isBroadcastUpdates() {
    return broadcastUpdates;
  }

  public MembershipCfg setBroadcastUpdates(final boolean broadcastUpdates) {
    this.broadcastUpdates = broadcastUpdates;
    return this;
  }

  public boolean isBroadcastDisputes() {
    return broadcastDisputes;
  }

  public MembershipCfg setBroadcastDisputes(final boolean broadcastDisputes) {
    this.broadcastDisputes = broadcastDisputes;
    return this;
  }

  public boolean isNotifySuspect() {
    return notifySuspect;
  }

  public MembershipCfg setNotifySuspect(final boolean notifySuspect) {
    this.notifySuspect = notifySuspect;
    return this;
  }

  public Duration getGossipInterval() {
    return gossipInterval;
  }

  public MembershipCfg setGossipInterval(final Duration gossipInterval) {
    this.gossipInterval = gossipInterval;
    return this;
  }

  public int getGossipFanout() {
    return gossipFanout;
  }

  public MembershipCfg setGossipFanout(final int gossipFanout) {
    this.gossipFanout = gossipFanout;
    return this;
  }

  public Duration getProbeInterval() {
    return probeInterval;
  }

  public MembershipCfg setProbeInterval(final Duration probeInterval) {
    this.probeInterval = probeInterval;
    return this;
  }

  public Duration getProbeTimeout() {
    return probeTimeout;
  }

  public MembershipCfg setProbeTimeout(final Duration probeTimeout) {
    this.probeTimeout = probeTimeout;
    return this;
  }

  public int getSuspectProbes() {
    return suspectProbes;
  }

  public MembershipCfg setSuspectProbes(final int suspectProbes) {
    this.suspectProbes = suspectProbes;
    return this;
  }

  public Duration getFailureTimeout() {
    return failureTimeout;
  }

  public MembershipCfg setFailureTimeout(final Duration failureTimeout) {
    this.failureTimeout = failureTimeout;
    return this;
  }

  public Duration getSyncInterval() {
    return syncInterval;
  }

  public MembershipCfg setSyncInterval(final Duration syncInterval) {
    this.syncInterval = syncInterval;
    return this;
  }

  @Override
  public int hashCode() {
    int result = (broadcastUpdates ? 1 : 0);
    result = 31 * result + (broadcastDisputes ? 1 : 0);
    result = 31 * result + (notifySuspect ? 1 : 0);
    result = 31 * result + (gossipInterval != null ? gossipInterval.hashCode() : 0);
    result = 31 * result + gossipFanout;
    result = 31 * result + (probeInterval != null ? probeInterval.hashCode() : 0);
    result = 31 * result + (probeTimeout != null ? probeTimeout.hashCode() : 0);
    result = 31 * result + suspectProbes;
    result = 31 * result + (failureTimeout != null ? failureTimeout.hashCode() : 0);
    result = 31 * result + (syncInterval != null ? syncInterval.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final MembershipCfg that = (MembershipCfg) o;

    if (broadcastUpdates != that.broadcastUpdates) {
      return false;
    }
    if (broadcastDisputes != that.broadcastDisputes) {
      return false;
    }
    if (notifySuspect != that.notifySuspect) {
      return false;
    }
    if (gossipFanout != that.gossipFanout) {
      return false;
    }
    if (suspectProbes != that.suspectProbes) {
      return false;
    }
    if (gossipInterval != null
        ? !gossipInterval.equals(that.gossipInterval)
        : that.gossipInterval != null) {
      return false;
    }
    if (probeInterval != null
        ? !probeInterval.equals(that.probeInterval)
        : that.probeInterval != null) {
      return false;
    }
    if (probeTimeout != null
        ? !probeTimeout.equals(that.probeTimeout)
        : that.probeTimeout != null) {
      return false;
    }
    if (failureTimeout != null
        ? !failureTimeout.equals(that.failureTimeout)
        : that.failureTimeout != null) {
      return false;
    }
    return syncInterval != null
        ? syncInterval.equals(that.syncInterval)
        : that.syncInterval == null;
  }

  @Override
  public String toString() {
    return "MembershipCfg{"
        + "broadcastUpdates="
        + broadcastUpdates
        + ", broadcastDisputes="
        + broadcastDisputes
        + ", notifySuspect="
        + notifySuspect
        + ", gossipInterval="
        + gossipInterval
        + ", gossipFanout="
        + gossipFanout
        + ", probeInterval="
        + probeInterval
        + ", probeTimeout="
        + probeTimeout
        + ", suspectProbes="
        + suspectProbes
        + ", failureTimeout="
        + failureTimeout
        + ", syncInterval="
        + syncInterval
        + '}';
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class Membership implements Cloneable {
  private static final String PREFIX = "camunda.cluster.membership";

  private static final Map<String, String> LEGACY_GATEWAY_PROPERTIES =
      Map.of(
          "broadcastUpdates", "zeebe.gateway.cluster.membership.broadcastUpdates",
          "broadcastDisputes", "zeebe.gateway.cluster.membership.broadcastDisputes",
          "notifySuspect", "zeebe.gateway.cluster.membership.notifySuspect",
          "gossipInterval", "zeebe.gateway.cluster.membership.gossipInterval",
          "gossipFanout", "zeebe.gateway.cluster.membership.gossipFanout",
          "probeInterval", "zeebe.gateway.cluster.membership.probeInterval",
          "probeTimeout", "zeebe.gateway.cluster.membership.probeTimeout",
          "suspectProbes", "zeebe.gateway.cluster.membership.suspectProbes",
          "failureTimeout", "zeebe.gateway.cluster.membership.failureTimeout",
          "syncInterval", "zeebe.gateway.cluster.membership.syncInterval");

  private static final Map<String, String> LEGACY_BROKER_PROPERTIES =
      Map.of(
          "broadcastUpdates", "zeebe.broker.cluster.membership.broadcastUpdates",
          "broadcastDisputes", "zeebe.broker.cluster.membership.broadcastDisputes",
          "notifySuspect", "zeebe.broker.cluster.membership.notifySuspect",
          "gossipInterval", "zeebe.broker.cluster.membership.gossipInterval",
          "gossipFanout", "zeebe.broker.cluster.membership.gossipFanout",
          "probeInterval", "zeebe.broker.cluster.membership.probeInterval",
          "probeTimeout", "zeebe.broker.cluster.membership.probeTimeout",
          "suspectProbes", "zeebe.broker.cluster.membership.suspectProbes",
          "failureTimeout", "zeebe.broker.cluster.membership.failureTimeout",
          "syncInterval", "zeebe.broker.cluster.membership.syncInterval");

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_PROPERTIES;

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
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".broadcast-updates",
        broadcastUpdates,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("broadcastUpdates")));
  }

  public void setBroadcastUpdates(final boolean broadcastUpdates) {
    this.broadcastUpdates = broadcastUpdates;
  }

  public boolean isBroadcastDisputes() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".broadcast-disputes",
        broadcastDisputes,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("broadcastDisputes")));
  }

  public void setBroadcastDisputes(final boolean broadcastDisputes) {
    this.broadcastDisputes = broadcastDisputes;
  }

  public boolean isNotifySuspect() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".notify-suspect",
        notifySuspect,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("notifySuspect")));
  }

  public void setNotifySuspect(final boolean notifySuspect) {
    this.notifySuspect = notifySuspect;
  }

  public Duration getGossipInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".gossip-interval",
        gossipInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("gossipInterval")));
  }

  public void setGossipInterval(final Duration gossipInterval) {
    this.gossipInterval = gossipInterval;
  }

  public int getGossipFanout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".gossip-fanout",
        gossipFanout,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("gossipFanout")));
  }

  public void setGossipFanout(final int gossipFanout) {
    this.gossipFanout = gossipFanout;
  }

  public Duration getProbeInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".probe-interval",
        probeInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("probeInterval")));
  }

  public void setProbeInterval(final Duration probeInterval) {
    this.probeInterval = probeInterval;
  }

  public Duration getProbeTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".probe-timeout",
        probeTimeout,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("probeTimeout")));
  }

  public void setProbeTimeout(final Duration probeTimeout) {
    this.probeTimeout = probeTimeout;
  }

  public int getSuspectProbes() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".suspect-probes",
        suspectProbes,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("suspectProbes")));
  }

  public void setSuspectProbes(final int suspectProbes) {
    this.suspectProbes = suspectProbes;
  }

  public Duration getFailureTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".failure-timeout",
        failureTimeout,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("failureTimeout")));
  }

  public void setFailureTimeout(final Duration failureTimeout) {
    this.failureTimeout = failureTimeout;
  }

  public Duration getSyncInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".sync-interval",
        syncInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("syncInterval")));
  }

  public void setSyncInterval(final Duration syncInterval) {
    this.syncInterval = syncInterval;
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (final CloneNotSupportedException e) {
      throw new AssertionError("Unexpected: Class must implement Cloneable", e);
    }
  }

  public Membership withBrokerMembershipProperties() {
    final var copy = (Membership) clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_PROPERTIES;
    return copy;
  }

  public Membership withGatewayMembershipProperties() {
    final var copy = (Membership) clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_PROPERTIES;
    return copy;
  }
}

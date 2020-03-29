/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.protocol;

import java.time.Duration;

/** SWIM membership protocol builder. */
public class SwimMembershipProtocolBuilder extends GroupMembershipProtocolBuilder {
  private final SwimMembershipProtocolConfig config = new SwimMembershipProtocolConfig();

  /**
   * Sets whether to broadcast member updates to all peers.
   *
   * @param broadcastUpdates whether to broadcast member updates to all peers
   * @return the protocol builder
   */
  public SwimMembershipProtocolBuilder withBroadcastUpdates(final boolean broadcastUpdates) {
    config.setBroadcastUpdates(broadcastUpdates);
    return this;
  }

  /**
   * Sets whether to broadcast disputes to all peers.
   *
   * @param broadcastDisputes whether to broadcast disputes to all peers
   * @return the protocol builder
   */
  public SwimMembershipProtocolBuilder withBroadcastDisputes(final boolean broadcastDisputes) {
    config.setBroadcastDisputes(broadcastDisputes);
    return this;
  }

  /**
   * Sets whether to notify a suspect node on state changes.
   *
   * @param notifySuspect whether to notify a suspect node on state changes
   * @return the protocol builder
   */
  public SwimMembershipProtocolBuilder withNotifySuspect(final boolean notifySuspect) {
    config.setNotifySuspect(notifySuspect);
    return this;
  }

  /**
   * Sets the gossip interval.
   *
   * @param gossipInterval the gossip interval
   * @return the protocol builder
   */
  public SwimMembershipProtocolBuilder withGossipInterval(final Duration gossipInterval) {
    config.setGossipInterval(gossipInterval);
    return this;
  }

  /**
   * Sets the gossip fanout.
   *
   * @param gossipFanout the gossip fanout
   * @return the protocol builder
   */
  public SwimMembershipProtocolBuilder withGossipFanout(final int gossipFanout) {
    config.setGossipFanout(gossipFanout);
    return this;
  }

  /**
   * Sets the probe interval.
   *
   * @param probeInterval the probe interval
   * @return the protocol builder
   */
  public SwimMembershipProtocolBuilder withProbeInterval(final Duration probeInterval) {
    config.setProbeInterval(probeInterval);
    return this;
  }

  /**
   * Sets the number of probes to perform on suspect members.
   *
   * @param suspectProbes the number of probes to perform on suspect members
   * @return the protocol builder
   */
  public SwimMembershipProtocolBuilder withSuspectProbes(final int suspectProbes) {
    config.setSuspectProbes(suspectProbes);
    return this;
  }

  /**
   * Sets the failure timeout to use prior to phi failure detectors being populated.
   *
   * @param failureTimeout the failure timeout
   * @return the protocol builder
   */
  public SwimMembershipProtocolBuilder withFailureTimeout(final Duration failureTimeout) {
    config.setFailureTimeout(failureTimeout);
    return this;
  }

  /**
   * Sets the sync interval to use to ensure a consistent view of a stable cluster within an upper
   * bound.
   *
   * @param syncInterval the interval at which nodes should sync with another, random node
   * @return the protocol builder
   */
  public SwimMembershipProtocolBuilder withSyncInterval(final Duration syncInterval) {
    config.setSyncInterval(syncInterval);
    return this;
  }

  @Override
  public GroupMembershipProtocol build() {
    return new SwimMembershipProtocol(config);
  }
}

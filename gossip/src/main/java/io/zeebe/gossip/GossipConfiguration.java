/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gossip;

import io.zeebe.util.DurationUtil;
import java.time.Duration;

public class GossipConfiguration {
  private int retransmissionMultiplier = 3;

  private String probeInterval = "1s";
  private String probeTimeout = "500ms";

  private int probeIndirectNodes = 3;
  private String probeIndirectTimeout = "1s";

  private int suspicionMultiplier = 5;

  private String syncTimeout = "3s";
  private String syncInterval = "15s";

  private String joinTimeout = "1s";
  private String joinInterval = "1s";

  private String leaveTimeout = "1s";

  private int maxMembershipEventsPerMessage = 32;
  private int maxCustomEventsPerMessage = 8;

  /** The timeout of a join request. */
  public String getJoinTimeout() {
    return joinTimeout;
  }

  public Duration getJoinTimeoutDuration() {
    return DurationUtil.parse(joinTimeout);
  }

  /** The timeout of a join request. */
  public GossipConfiguration setJoinTimeout(String joinTimeout) {
    this.joinTimeout = joinTimeout;
    return this;
  }

  /** The time when a failed join request is send again. */
  public String getJoinInterval() {
    return joinInterval;
  }

  public Duration getJoinIntervalDuration() {
    return DurationUtil.parse(joinInterval);
  }

  /** The time when a failed join request is send again. */
  public GossipConfiguration setJoinInterval(String joinInterval) {
    this.joinInterval = joinInterval;
    return this;
  }

  /**
   * The multiplier for the number of retransmissions of a gossip message. The number of retransmits
   * is calculated as {@code retransmissionMultiplier * log(n + 1)}, where n is the number of nodes
   * in the cluster.
   */
  public int getRetransmissionMultiplier() {
    return retransmissionMultiplier;
  }

  /**
   * The multiplier for the number of retransmissions of a gossip message. The number of retransmits
   * is calculated as {@code retransmissionMultiplier * log(n + 1)}, where n is the number of nodes
   * in the cluster.
   */
  public GossipConfiguration setRetransmissionMultiplier(final int retransmissionMultiplier) {
    this.retransmissionMultiplier = retransmissionMultiplier;
    return this;
  }

  /** The time between two probe requests. */
  public String getProbeInterval() {
    return probeInterval;
  }

  /** The time between two probe requests. */
  public GossipConfiguration setProbeInterval(final String probeInterval) {
    this.probeInterval = probeInterval;
    return this;
  }

  /** The timeout of a probe request. */
  public String getProbeTimeout() {
    return probeTimeout;
  }

  public Duration getProbeTimeoutDuration() {
    return DurationUtil.parse(probeTimeout);
  }

  /** The timeout of a probe request. */
  public GossipConfiguration setProbeTimeout(final String probeTimeout) {
    this.probeTimeout = probeTimeout;
    return this;
  }

  /** The number of nodes to send an indirect probe request to. */
  public int getProbeIndirectNodes() {
    return probeIndirectNodes;
  }

  /** The number of nodes to send an indirect probe request to. */
  public GossipConfiguration setProbeIndirectNodes(final int probeIndirectNodes) {
    this.probeIndirectNodes = probeIndirectNodes;
    return this;
  }

  /** The timeout of an indirect probe request. */
  public String getProbeIndirectTimeout() {
    return probeIndirectTimeout;
  }

  public Duration getProbeIndirectTimeoutDuration() {
    return DurationUtil.parse(probeIndirectTimeout);
  }

  /** The timeout of an indirect probe request. */
  public GossipConfiguration probeIndirectTimeout(String probeIndirectTimeout) {
    this.probeIndirectTimeout = probeIndirectTimeout;
    return this;
  }

  /** The timeout of a sync request. */
  public String getSyncTimeout() {
    return syncTimeout;
  }

  public Duration getSyncTimeoutDuration() {
    return DurationUtil.parse(syncTimeout);
  }

  /** The interval on which the sync requests are send. */
  public String getSyncInterval() {
    return syncInterval;
  }

  public Duration getSyncIntervalDuration() {
    return DurationUtil.parse(syncInterval);
  }

  public GossipConfiguration setSyncInterval(String syncInterval) {
    this.syncInterval = syncInterval;
    return this;
  }

  /** The timeout of a sync request. */
  public GossipConfiguration syncTimeout(String syncTimeout) {
    this.syncTimeout = syncTimeout;
    return this;
  }

  /**
   * The multiplier for the suspicion timeout of a node. The suspicion timeout of a node is
   * calculated as {@code suspicionMultiplier * log(n + 1) * probeInterval}, where n is the number
   * of nodes in the cluster.
   */
  public int getSuspicionMultiplier() {
    return suspicionMultiplier;
  }

  /**
   * The multiplier for the suspicion timeout of a node. The suspicion timeout of a node is
   * calculated as {@code suspicionMultiplier * log(n + 1) * probeInterval}, where n is the number
   * of nodes in the cluster.
   */
  public GossipConfiguration setSuspicionMultiplier(final int suspicionMultiplier) {
    this.suspicionMultiplier = suspicionMultiplier;
    return this;
  }

  /** The timeout of a leave request. */
  public String getLeaveTimeout() {
    return leaveTimeout;
  }

  public Duration getLeaveTimeoutDuration() {
    return DurationUtil.parse(leaveTimeout);
  }

  /** The timeout of a leave request. */
  public GossipConfiguration setLeaveTimeout(String leaveTimeout) {
    this.leaveTimeout = leaveTimeout;
    return this;
  }

  /** The maximum amount of membership evens in a gossip message. */
  public int getMaxMembershipEventsPerMessage() {
    return maxMembershipEventsPerMessage;
  }

  /** The maximum amount of membership evens in a gossip message. */
  public GossipConfiguration maxMembershipEventsPerMessage(int maxMembershipEventsPerMessage) {
    this.maxMembershipEventsPerMessage = maxMembershipEventsPerMessage;
    return this;
  }

  /** The maximum amount of custom evens in a gossip message. */
  public int getMaxCustomEventsPerMessage() {
    return maxCustomEventsPerMessage;
  }

  /** The maximum amount of custom evens in a gossip message. */
  public GossipConfiguration maxCustomEventsPerMessage(int maxCustomEventsPerMessage) {
    this.maxCustomEventsPerMessage = maxCustomEventsPerMessage;
    return this;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("GossipConfiguration [retransmissionMultiplier=");
    builder.append(retransmissionMultiplier);
    builder.append(", probeInterval=");
    builder.append(probeInterval);
    builder.append(", probeTimeout=");
    builder.append(probeTimeout);
    builder.append(", probeIndirectNodes=");
    builder.append(probeIndirectNodes);
    builder.append(", probeIndirectTimeout=");
    builder.append(probeIndirectTimeout);
    builder.append(", suspicionMultiplier=");
    builder.append(suspicionMultiplier);
    builder.append(", syncTimeout=");
    builder.append(syncTimeout);
    builder.append(", joinTimeout=");
    builder.append(joinTimeout);
    builder.append(", joinInterval=");
    builder.append(joinInterval);
    builder.append(", leaveTimeout=");
    builder.append(leaveTimeout);
    builder.append(", maxMembershipEventsPerMessage=");
    builder.append(maxMembershipEventsPerMessage);
    builder.append(", maxCustomEventsPerMessage=");
    builder.append(maxCustomEventsPerMessage);
    builder.append("]");
    return builder.toString();
  }

  public Duration getProbeIntervalDuration() {
    return DurationUtil.parse(probeInterval);
  }
}

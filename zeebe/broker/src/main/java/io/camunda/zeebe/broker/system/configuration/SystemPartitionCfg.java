/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;

/**
 * Proof of concept of the system partition (design {@code
 * docs/system-partition/system-partition-reconfiguration.md}): a Raft partition replicated on every
 * broker, in which only some brokers are voters. Disabled by default.
 */
public final class SystemPartitionCfg {

  private boolean enabled = false;
  private Duration membershipCheckInterval = Duration.ofMillis(500);
  private Duration leaveTimeout = Duration.ofSeconds(30);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * @return how often the leader of the system partition checks its members (design 5.5)
   */
  public Duration getMembershipCheckInterval() {
    return membershipCheckInterval;
  }

  public void setMembershipCheckInterval(final Duration membershipCheckInterval) {
    this.membershipCheckInterval = membershipCheckInterval;
  }

  /**
   * @return how long {@code MemberLeave} and phase 0 wait for the leader before they fail and are
   *     retried (design 5.6.2)
   */
  public Duration getLeaveTimeout() {
    return leaveTimeout;
  }

  public void setLeaveTimeout(final Duration leaveTimeout) {
    this.leaveTimeout = leaveTimeout;
  }

  @Override
  public String toString() {
    return "SystemPartitionCfg{enabled="
        + enabled
        + ", membershipCheckInterval="
        + membershipCheckInterval
        + ", leaveTimeout="
        + leaveTimeout
        + '}';
  }
}

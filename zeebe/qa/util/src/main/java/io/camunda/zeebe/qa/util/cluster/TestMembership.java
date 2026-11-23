/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import java.time.Duration;

/**
 * Applies the cluster membership settings used by tests, so that a stopped node is detected quickly
 * instead of after the production default of 10s.
 *
 * <p>Membership is detected through SWIM: a member that does not answer is declared dead once the
 * failure timeout elapsed, and membership updates are synced every sync interval. Both default to
 * 10s, which dominates every test that stops a broker and waits for the rest of the cluster to
 * notice. The values here are the ones {@code TopologyFaultToleranceIT} uses for exactly that
 * detection.
 *
 * <p>There are two overloads because the unified configuration and the legacy gateway configuration
 * each have their own membership type with the same properties.
 */
public final class TestMembership {

  private static final Duration FAILURE_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration SYNC_INTERVAL = Duration.ofSeconds(1);
  private static final Duration PROBE_INTERVAL = Duration.ofMillis(250);
  private static final Duration PROBE_TIMEOUT = Duration.ofMillis(100);

  private TestMembership() {}

  /** Fast membership for a node configured through the unified configuration. */
  public static void configureFastMembership(
      final io.camunda.configuration.cluster.Membership membership) {
    membership.setFailureTimeout(FAILURE_TIMEOUT);
    membership.setSyncInterval(SYNC_INTERVAL);
    membership.setProbeInterval(PROBE_INTERVAL);
    membership.setProbeTimeout(PROBE_TIMEOUT);
  }

  /** Fast membership for a gateway configured through the legacy gateway configuration. */
  public static void configureFastMembership(
      final io.camunda.zeebe.gateway.impl.configuration.MembershipCfg membership) {
    membership.setFailureTimeout(FAILURE_TIMEOUT);
    membership.setSyncInterval(SYNC_INTERVAL);
    membership.setProbeInterval(PROBE_INTERVAL);
    membership.setProbeTimeout(PROBE_TIMEOUT);
  }
}

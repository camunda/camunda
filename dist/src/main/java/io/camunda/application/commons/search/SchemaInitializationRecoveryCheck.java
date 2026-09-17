/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/**
 * Whether a physical tenant's schema must be left alone because the tenant is being recovered.
 * Applying it mid-recovery is not merely redundant: an Elasticsearch/OpenSearch snapshot cannot be
 * restored into indices that already exist, so a node that creates them breaks the restore it came
 * up into.
 *
 * <p>Most of the answer belongs to {@link BrokerTopologyManager#isRecoveringOrUnknown(String)},
 * which already holds off on a tenant whose mode is not known yet wherever an answer is coming -
 * brokers of the tenant are visible in the cluster membership, so one of them will gossip the
 * configuration that carries it. This class adds the one thing a stateless check cannot: a bounded
 * wait for the case where that does not hold.
 *
 * <p>That case is a node that has discovered no broker of the tenant, and it is ambiguous exactly
 * once, at startup: either there genuinely is no cluster - nothing is recovering, and something has
 * to create the schema - or discovery simply has not completed yet, in which case answering now
 * races a restore. Discovery is network I/O that normally lands in well under a second, so a short
 * wait resolves the ambiguity almost always, and expires into today's behaviour when it does not.
 *
 * <p>The wait is bounded rather than open-ended because a node that can see no cluster is not one
 * that should park: on a gateway, broker visibility is a liveness concern ({@code
 * livenessGatewayClusterAwareness}), so such a node is restarted by its orchestrator rather than
 * left waiting. An unbounded wait would only add a second, redundant signal on the readiness probe
 * - and would never get to lift.
 *
 * <p>A broker is covered by the same wait rather than by a guarantee of its own. Its cluster
 * configuration is loaded by the second step of its startup, so the grace is ample; the residual
 * case is a broker so wedged that it has not read a local file within the grace, which is a node in
 * trouble for reasons this check cannot repair.
 */
@NullMarked
public final class SchemaInitializationRecoveryCheck implements Predicate<String> {

  /**
   * Comfortably longer than cluster membership discovery, and comfortably shorter than the grace
   * the gateway's liveness indicators allow before a restart - a wait that outlasts those would be
   * decided by the orchestrator rather than by this check.
   */
  @VisibleForTesting static final Duration DEFAULT_DISCOVERY_GRACE = Duration.ofSeconds(5);

  private final BrokerTopologyManager topologyManager;
  private final long graceExpiresAtNanos;

  public SchemaInitializationRecoveryCheck(final BrokerTopologyManager topologyManager) {
    this(topologyManager, DEFAULT_DISCOVERY_GRACE);
  }

  @VisibleForTesting
  SchemaInitializationRecoveryCheck(
      final BrokerTopologyManager topologyManager, final Duration discoveryGrace) {
    this.topologyManager = topologyManager;
    graceExpiresAtNanos = System.nanoTime() + discoveryGrace.toNanos();
  }

  @Override
  public boolean test(final String physicalTenantId) {
    if (topologyManager.isRecoveringOrUnknown(physicalTenantId)) {
      return true;
    }
    return topologyManager.isRecovering(physicalTenantId) && withinDiscoveryGrace();
  }

  private boolean withinDiscoveryGrace() {
    return System.nanoTime() - graceExpiresAtNanos < 0;
  }
}

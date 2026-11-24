/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.mtls;

import io.camunda.zeebe.util.tls.PeerIdentity;
import io.camunda.zeebe.util.tls.PeerVerificationListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;

/**
 * Logs and counts every client certificate check. Rejections are tagged by reason only, and
 * acceptances by the allow-list entry that matched, so tag cardinality stays bounded by
 * configuration; the presented identity itself only goes into the log.
 */
public final class MeteredPeerVerificationListener implements PeerVerificationListener {

  private final MeterRegistry registry;
  private final String metricPrefix;
  private final Logger logger;

  /**
   * @param metricPrefix e.g. {@code zeebe.gateway.grpc.client.certificate}
   */
  public MeteredPeerVerificationListener(
      final MeterRegistry registry, final String metricPrefix, final Logger logger) {
    this.registry = registry;
    this.metricPrefix = metricPrefix;
    this.logger = logger;
  }

  @Override
  public void onAccepted(final PeerIdentity identity, final String matchedIdentity) {
    logger.debug(
        "Accepted peer certificate {} matching allowed identity '{}'", identity, matchedIdentity);
    Counter.builder(metricPrefix + ".accepted")
        .description("Peer certificates which passed verification, by matched allowed identity")
        .tag("identity", matchedIdentity)
        .register(registry)
        .increment();
  }

  @Override
  public void onRejected(
      final PeerIdentity identity,
      final RejectionReason reason,
      final String detail,
      final boolean enforced) {
    if (enforced) {
      logger.warn("Rejected peer certificate {}: {} ({})", identity, reason, detail);
    } else {
      logger.warn(
          "Peer certificate {} would be rejected once client certificates are required: {} ({})",
          identity,
          reason,
          detail);
    }
    Counter.builder(metricPrefix + ".rejected")
        .description("Peer certificates which failed verification, by reason")
        .tag("reason", reason.name())
        .tag("enforced", String.valueOf(enforced))
        .register(registry)
        .increment();
  }
}

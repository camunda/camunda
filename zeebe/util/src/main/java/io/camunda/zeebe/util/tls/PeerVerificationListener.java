/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.tls;

/** Observes the outcome of every peer certificate check, for logging and metrics. */
public interface PeerVerificationListener {

  PeerVerificationListener NOOP =
      new PeerVerificationListener() {
        @Override
        public void onAccepted(final PeerIdentity identity, final String matchedIdentity) {}

        @Override
        public void onRejected(
            final PeerIdentity identity,
            final RejectionReason reason,
            final String detail,
            final boolean enforced) {}
      };

  /**
   * @param matchedIdentity the allow-list entry that matched, or {@code *} when no allow-list is
   *     configured; bounded by configuration, so safe to use as a metric tag
   */
  void onAccepted(PeerIdentity identity, String matchedIdentity);

  /**
   * @param enforced false when the check failed but the connection is let through anyway, see
   *     {@link ClientCertificateMode#OPTIONAL}
   */
  void onRejected(PeerIdentity identity, RejectionReason reason, String detail, boolean enforced);

  enum RejectionReason {
    EXPIRED,
    NOT_YET_VALID,
    UNTRUSTED,
    IDENTITY_NOT_ALLOWED
  }
}

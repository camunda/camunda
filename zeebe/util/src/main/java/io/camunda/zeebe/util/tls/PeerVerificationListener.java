/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.tls;

/**
 * Observes peer certificate checks from within the trust manager. The trust manager does not know
 * the peer's address in every setup (e.g. gRPC server engines), so servers that can observe the
 * handshake themselves should do their reporting there and use {@link #NOOP}.
 */
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
    IDENTITY_NOT_ALLOWED,
    /** The peer presented no certificate although one is required. */
    NO_CERTIFICATE,
    /** Any other handshake failure, e.g. a plaintext client or a protocol mismatch. */
    HANDSHAKE_FAILED
  }
}

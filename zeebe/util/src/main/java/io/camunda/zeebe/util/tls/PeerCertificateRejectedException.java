/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.tls;

import io.camunda.zeebe.util.tls.PeerVerificationListener.RejectionReason;
import java.security.cert.CertificateException;
import org.jspecify.annotations.Nullable;

/**
 * Why a peer certificate was rejected, and whose it was. Thrown from, or attached to the exception
 * thrown from, {@link PeerCertificateTrustManager}, so that whoever observes the failed handshake
 * (which knows the peer's address) can report it.
 */
public final class PeerCertificateRejectedException extends CertificateException {

  private final PeerIdentity identity;
  private final RejectionReason reason;
  private final String detail;

  public PeerCertificateRejectedException(
      final PeerIdentity identity, final RejectionReason reason, final String detail) {
    super(
        "Rejected peer certificate %s: %s (%s)"
            .formatted(identity, reason, PeerIdentity.sanitize(detail)));
    this.identity = identity;
    this.reason = reason;
    // may embed presented identities, so it is sanitized like them
    this.detail = PeerIdentity.sanitize(detail);
  }

  public PeerIdentity identity() {
    return identity;
  }

  public RejectionReason reason() {
    return reason;
  }

  public String detail() {
    return detail;
  }

  /** Finds the rejection in the cause chain, or the suppressed exceptions, of a failure. */
  public static @Nullable PeerCertificateRejectedException find(final Throwable failure) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t instanceof final PeerCertificateRejectedException rejection) {
        return rejection;
      }
      for (final var suppressed : t.getSuppressed()) {
        if (suppressed instanceof final PeerCertificateRejectedException rejection) {
          return rejection;
        }
      }
    }
    return null;
  }
}

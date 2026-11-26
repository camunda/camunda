/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.tls;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matches a peer's certificate identity against an allow-list.
 *
 * <p>Identities match on Subject Alternative Names (DNS, IP, URI, email), exactly and ignoring
 * ASCII case. The subject CN is only considered when {@code commonNameFallback} is enabled and the
 * certificate has no SAN extension at all, following RFC 9525. An empty allow-list matches any
 * certificate.
 *
 * <p>Shared by every server doing mutual TLS, so the same certificate gets the same verdict on the
 * gRPC API, the REST API and between cluster nodes.
 */
public final class PeerIdentityMatcher {

  /** Reported as the matched identity when there is no allow-list. */
  public static final String ANY_IDENTITY = "*";

  private final Set<String> allowedIdentities;
  private final boolean commonNameFallback;

  /**
   * @throws IllegalArgumentException if an entry is blank or not ASCII; a blank entry from e.g. an
   *     empty Helm value would otherwise silently turn into "allow any"
   */
  public PeerIdentityMatcher(
      final Collection<String> allowedIdentities, final boolean commonNameFallback) {
    this.allowedIdentities =
        allowedIdentities.stream()
            .map(PeerIdentityMatcher::normalizeAllowedIdentity)
            .collect(Collectors.toUnmodifiableSet());
    this.commonNameFallback = commonNameFallback;
  }

  /**
   * The allow-list entry the identity matches, or {@link #ANY_IDENTITY} when there is no
   * allow-list; empty when it is not allowed. Bounded by configuration, so usable as a metric tag.
   */
  public Optional<String> match(final PeerIdentity identity) {
    if (allowedIdentities.isEmpty()) {
      return Optional.of(ANY_IDENTITY);
    }

    return candidates(identity).stream()
        // fold ASCII case only: Unicode folding maps look-alikes (e.g. KELVIN SIGN) onto ASCII
        .filter(PeerIdentityMatcher::isAscii)
        .map(s -> s.toLowerCase(Locale.ROOT))
        .filter(allowedIdentities::contains)
        .findFirst();
  }

  /**
   * The SANs, or the CN when fallback is enabled and the certificate has no SAN extension at all;
   * as in RFC 6125/9525, a CN is never considered next to SANs, which are the vetted field.
   */
  public List<String> candidates(final PeerIdentity identity) {
    if (commonNameFallback
        && !identity.hasSubjectAlternativeNameExtension()
        && identity.commonName() != null) {
      return List.of(identity.commonName());
    }
    return identity.subjectAlternativeNames();
  }

  private static String normalizeAllowedIdentity(final String identity) {
    final var trimmed = identity == null ? "" : identity.trim();
    if (trimmed.isEmpty() || !isAscii(trimmed) || ANY_IDENTITY.equals(trimmed)) {
      throw new IllegalArgumentException(
          "Expected allowed identities to be non-blank ASCII SAN values (DNS name, IP, URI or"
              + " email), but got '%s'; leave the list empty to allow any trusted certificate"
                  .formatted(identity));
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }

  private static boolean isAscii(final String value) {
    return value.chars().allMatch(c -> c < 0x80);
  }
}

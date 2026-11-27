/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.tls;

import io.camunda.zeebe.util.tls.PeerVerificationListener.RejectionReason;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.naming.NamingException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import org.jspecify.annotations.Nullable;

/**
 * Verifies a peer's certificate chain against a bundle of trusted CAs, then matches the leaf
 * certificate's identity against an allow-list.
 *
 * <p>Identities match on Subject Alternative Names (DNS, IP, URI, email), exactly and ignoring
 * case, following RFC 9525. The subject CN is only considered when {@code commonNameFallback} is
 * enabled. An empty allow-list accepts any certificate signed by a trusted CA.
 *
 * <p>The same instance verifies peers in both directions: as a server ({@code checkClientTrusted})
 * and as a client ({@code checkServerTrusted}), which is what node-to-node mTLS needs. Hostname
 * verification on the client side is left to the delegate, so it still applies when the engine's
 * endpoint identification algorithm is set.
 *
 * <p>When enforcing, a failed check aborts the handshake. The thrown exception keeps the JDK type
 * TLS libraries map to a specific alert (e.g. {@link CertificateExpiredException} to {@code
 * certificate_expired}), so the peer learns the cause, and carries a {@link
 * PeerCertificateRejectedException} (as cause or suppressed) with the identity and reason, so the
 * server can log and count it. When not enforcing, failures are only reported to the listener; this
 * is meant for a permissive rollout mode.
 */
public final class PeerCertificateTrustManager extends X509ExtendedTrustManager {

  private static final int SAN_EMAIL = 1;
  private static final int SAN_DNS = 2;
  private static final int SAN_URI = 6;
  private static final int SAN_IP = 7;
  private static final String ANY_IDENTITY = "*";
  private static final String SAN_EXTENSION_OID = "2.5.29.17";

  private final X509ExtendedTrustManager delegate;
  private final Set<String> allowedIdentities;
  private final boolean commonNameFallback;
  private final boolean enforce;
  private final boolean advertiseTrustedIssuers;
  private final PeerVerificationListener listener;

  private PeerCertificateTrustManager(
      final X509ExtendedTrustManager delegate,
      final Collection<String> allowedIdentities,
      final boolean commonNameFallback,
      final boolean enforce,
      final boolean advertiseTrustedIssuers,
      final PeerVerificationListener listener) {
    this.delegate = delegate;
    this.allowedIdentities =
        allowedIdentities.stream()
            .map(PeerCertificateTrustManager::normalizeAllowedIdentity)
            .collect(Collectors.toUnmodifiableSet());
    this.commonNameFallback = commonNameFallback;
    this.enforce = enforce;
    this.advertiseTrustedIssuers = advertiseTrustedIssuers;
    this.listener = listener;
  }

  /**
   * @param trustedCertificates the CAs to trust; a certificate signed by any of them is accepted
   * @param enforce whether a failed check aborts the handshake, or is only reported
   * @param advertiseTrustedIssuers whether to name the trusted CAs in the certificate request, see
   *     {@link #getAcceptedIssuers()}
   */
  public static PeerCertificateTrustManager create(
      final Collection<X509Certificate> trustedCertificates,
      final Collection<String> allowedIdentities,
      final boolean commonNameFallback,
      final boolean enforce,
      final boolean advertiseTrustedIssuers,
      final PeerVerificationListener listener)
      throws GeneralSecurityException, IOException {
    if (trustedCertificates.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected at least one trusted CA certificate to verify peers with, but none given");
    }

    final var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null);
    var i = 0;
    for (final var certificate : trustedCertificates) {
      keyStore.setCertificateEntry("ca-" + i++, certificate);
    }

    final var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(keyStore);
    final var delegate =
        Arrays.stream(factory.getTrustManagers())
            .filter(X509ExtendedTrustManager.class::isInstance)
            .map(X509ExtendedTrustManager.class::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No X509 trust manager available"));

    return new PeerCertificateTrustManager(
        delegate,
        allowedIdentities,
        commonNameFallback,
        enforce,
        advertiseTrustedIssuers,
        listener);
  }

  /** Reads every certificate in a PEM (or DER) file; a bundle may hold several CAs. */
  public static List<X509Certificate> readCertificates(final File file)
      throws IOException, CertificateException {
    try (final InputStream in = Files.newInputStream(file.toPath())) {
      final Collection<? extends Certificate> certificates =
          CertificateFactory.getInstance("X.509").generateCertificates(in);
      final var result = new ArrayList<X509Certificate>(certificates.size());
      for (final var certificate : certificates) {
        result.add((X509Certificate) certificate);
      }
      return result;
    }
  }

  @Override
  public void checkClientTrusted(
      final X509Certificate[] chain, final String authType, final @Nullable Socket socket)
      throws CertificateException {
    verify(chain, peerOf(socket), () -> delegate.checkClientTrusted(chain, authType, socket));
  }

  @Override
  public void checkServerTrusted(
      final X509Certificate[] chain, final String authType, final @Nullable Socket socket)
      throws CertificateException {
    verify(chain, peerOf(socket), () -> delegate.checkServerTrusted(chain, authType, socket));
  }

  @Override
  public void checkClientTrusted(
      final X509Certificate[] chain, final String authType, final @Nullable SSLEngine engine)
      throws CertificateException {
    verify(chain, peerOf(engine), () -> delegate.checkClientTrusted(chain, authType, engine));
  }

  @Override
  public void checkServerTrusted(
      final X509Certificate[] chain, final String authType, final @Nullable SSLEngine engine)
      throws CertificateException {
    verify(chain, peerOf(engine), () -> delegate.checkServerTrusted(chain, authType, engine));
  }

  @Override
  public void checkClientTrusted(final X509Certificate[] chain, final String authType)
      throws CertificateException {
    verify(chain, null, () -> delegate.checkClientTrusted(chain, authType));
  }

  @Override
  public void checkServerTrusted(final X509Certificate[] chain, final String authType)
      throws CertificateException {
    verify(chain, null, () -> delegate.checkServerTrusted(chain, authType));
  }

  /**
   * The CA names sent in the server's certificate request. Clients only present a certificate
   * issued by one of them, which lets a client holding several certificates pick the right one, and
   * lets browsers skip the certificate picker. But a client whose certificate comes from any other
   * CA then presents nothing, so the server can only say "no certificate" instead of naming the
   * certificate and why it is not trusted. An empty list makes every client present what it has.
   */
  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return advertiseTrustedIssuers ? delegate.getAcceptedIssuers() : new X509Certificate[0];
  }

  /**
   * The allow-list entry the certificate matches, or {@code *} when there is no allow-list; empty
   * when it is not allowed. Bounded by configuration, so usable as a metric tag.
   */
  public Optional<String> matchingIdentity(final PeerIdentity identity) {
    return matchIdentity(identity);
  }

  private void verify(
      final X509Certificate[] chain,
      final @Nullable String peerAddress,
      final ChainCheck chainCheck)
      throws CertificateException {
    if (chain == null || chain.length == 0) {
      // the delegate produces the right error for an empty chain; nothing to report on
      chainCheck.run();
      return;
    }

    final var leaf = chain[0];
    final var identity = identityOf(leaf, peerAddress);
    if (identity.malformed()) {
      reject(
          identity,
          RejectionReason.MALFORMED_CERTIFICATE,
          "the subject alternative name extension could not be parsed",
          null);
      return;
    }

    try {
      leaf.checkValidity();
    } catch (final CertificateExpiredException e) {
      reject(
          identity,
          RejectionReason.EXPIRED,
          e.getMessage(),
          new CertificateExpiredException(e.getMessage()));
      return;
    } catch (final CertificateNotYetValidException e) {
      reject(
          identity,
          RejectionReason.NOT_YET_VALID,
          e.getMessage(),
          new CertificateNotYetValidException(e.getMessage()));
      return;
    }

    try {
      chainCheck.run();
    } catch (final CertificateException e) {
      reject(identity, RejectionReason.UNTRUSTED, e.getMessage(), e);
      return;
    }

    final var matched = matchIdentity(identity);
    if (matched.isEmpty()) {
      final var detail =
          "none of the certificate's identities %s are allowed"
              .formatted(candidateIdentities(identity));
      reject(identity, RejectionReason.IDENTITY_NOT_ALLOWED, detail, null);
      return;
    }

    listener.onAccepted(identity, matched.get());
  }

  /**
   * @param failure the exception to abort the handshake with, typed so the TLS library sends the
   *     matching alert; null to throw the rejection itself
   */
  private void reject(
      final PeerIdentity identity,
      final RejectionReason reason,
      final @Nullable String detail,
      final @Nullable CertificateException failure)
      throws CertificateException {
    final var rejection =
        new PeerCertificateRejectedException(identity, reason, String.valueOf(detail));
    if (!enforce) {
      listener.onRejected(identity, reason, rejection.detail(), false);
      return;
    }

    if (failure == null) {
      throw rejection;
    }
    failure.addSuppressed(rejection);
    throw failure;
  }

  private Optional<String> matchIdentity(final PeerIdentity identity) {
    if (allowedIdentities.isEmpty()) {
      return Optional.of(ANY_IDENTITY);
    }

    return candidateIdentities(identity).stream()
        // fold ASCII case only: Unicode folding maps look-alikes (e.g. KELVIN SIGN) onto ASCII
        .filter(PeerCertificateTrustManager::isAscii)
        .map(s -> s.toLowerCase(Locale.ROOT))
        .filter(allowedIdentities::contains)
        .findFirst();
  }

  /**
   * The SANs, or the CN when fallback is enabled and the certificate has no SAN extension at all;
   * as in RFC 6125/9525, a CN is never considered next to SANs, which are the vetted field.
   */
  private List<String> candidateIdentities(final PeerIdentity identity) {
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

  public static PeerIdentity identityOf(
      final X509Certificate certificate, final @Nullable String peerAddress) {
    final var subject = certificate.getSubjectX500Principal().getName();
    final var hasSanExtension = certificate.getExtensionValue(SAN_EXTENSION_OID) != null;
    final var sans = subjectAlternativeNames(certificate);
    final var commonNames = commonNames(subject);
    // an unparseable SAN extension could hide the identities it holds; an ambiguous CN (several,
    // or an unparseable subject) only means the CN fallback cannot match
    final var malformed = hasSanExtension && sans == null;
    return new PeerIdentity(
        subject,
        sans == null ? List.of() : sans,
        hasSanExtension,
        commonNames != null && commonNames.size() == 1 ? commonNames.getFirst() : null,
        malformed,
        peerAddress);
  }

  /** Null when the extension cannot be parsed: dropping it would hide identities. */
  private static @Nullable List<String> subjectAlternativeNames(final X509Certificate certificate) {
    final Collection<List<?>> entries;
    try {
      entries = certificate.getSubjectAlternativeNames();
    } catch (final CertificateParsingException e) {
      return null;
    }
    if (entries == null) {
      return List.of();
    }

    final var names = new ArrayList<String>();
    for (final var entry : entries) {
      final var type = (Integer) entry.get(0);
      if ((type == SAN_DNS || type == SAN_IP || type == SAN_URI || type == SAN_EMAIL)
          && entry.get(1) instanceof final String name) {
        names.add(name);
      }
    }
    return List.copyOf(names);
  }

  /**
   * All CN values, including those in multi-valued RDNs; null when the subject cannot be parsed.
   * Several CNs are ambiguous (tools disagree on which one is the identity), so none is used.
   */
  private static @Nullable List<String> commonNames(final String subject) {
    try {
      final var names = new ArrayList<String>();
      for (final Rdn rdn : new LdapName(subject).getRdns()) {
        final var attributes = rdn.toAttributes().get("CN");
        if (attributes != null) {
          for (var i = 0; i < attributes.size(); i++) {
            names.add(String.valueOf(attributes.get(i)));
          }
        }
      }
      return names;
    } catch (final NamingException e) {
      return null;
    }
  }

  private static @Nullable String peerOf(final @Nullable SSLEngine engine) {
    if (engine == null || engine.getPeerHost() == null) {
      return null;
    }
    return engine.getPeerHost() + ":" + engine.getPeerPort();
  }

  private static @Nullable String peerOf(final @Nullable Socket socket) {
    if (socket == null || socket.getRemoteSocketAddress() == null) {
      return null;
    }
    return socket.getRemoteSocketAddress().toString();
  }

  @FunctionalInterface
  private interface ChainCheck {
    void run() throws CertificateException;
  }
}

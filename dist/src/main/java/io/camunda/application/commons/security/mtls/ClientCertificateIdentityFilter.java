/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security.mtls;

import io.camunda.zeebe.util.tls.PeerIdentity;
import io.camunda.zeebe.util.tls.PeerIdentityMatcher;
import io.camunda.zeebe.util.tls.PeerVerificationListener.RejectionReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests whose TLS client certificate is not on the allow-list, with a 403 that names the
 * reason, before any other filter (including Spring Security) sees them.
 *
 * <p>Tomcat has already verified the certificate chain against Spring's trust store during the
 * handshake ({@code server.ssl.client-auth=want|need}); this only checks the identity, with the
 * same rules as the gRPC API. Requests without a certificate pass through: with {@code need} they
 * cannot get here, with {@code want} they continue to the regular authentication (OIDC, basic).
 *
 * <p>The verdict depends only on the certificate, so it is computed, logged and counted once per
 * certificate, not per request. It is deliberately not keyed by TLS session id: with TLS 1.2
 * session tickets the client chooses the id of a resumed session, so a client that learns another
 * client's id (sent in clear in TLS 1.2) could otherwise reuse that client's verdict.
 */
public final class ClientCertificateIdentityFilter extends OncePerRequestFilter {

  static final String CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
  static final String METRIC_PREFIX = "camunda.rest.client.certificate";

  private static final Logger LOG = LoggerFactory.getLogger(ClientCertificateIdentityFilter.class);
  private static final int MAX_CACHED_VERDICTS = 10_000;

  private final PeerIdentityMatcher matcher;
  private final MeterRegistry registry;
  private final Map<String, Verdict> verdictsByCertificate =
      Collections.synchronizedMap(
          new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, Verdict> eldest) {
              return size() > MAX_CACHED_VERDICTS;
            }
          });

  public ClientCertificateIdentityFilter(
      final PeerIdentityMatcher matcher, final MeterRegistry registry) {
    this.matcher = matcher;
    this.registry = registry;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final var certificates = (X509Certificate[]) request.getAttribute(CERTIFICATE_ATTRIBUTE);
    if (certificates == null || certificates.length == 0) {
      filterChain.doFilter(request, response);
      return;
    }

    final var leaf = certificates[0];
    final var peer = request.getRemoteAddr() + ":" + request.getRemotePort();
    final var verdict =
        verdictsByCertificate.computeIfAbsent(fingerprint(leaf), ignored -> evaluate(leaf, peer));

    if (verdict.allowed()) {
      filterChain.doFilter(request, response);
    } else {
      reject(response, verdict.reason());
    }
  }

  /**
   * ERROR and ASYNC dispatches are checked too: an error page or async continuation of a request
   * that never passed through this filter must not reach the application either.
   */
  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  private static String fingerprint(final X509Certificate certificate) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException("Failed to fingerprint the client certificate", e);
    }
  }

  private Verdict evaluate(final X509Certificate leaf, final String peer) {
    final var identity = PeerIdentity.of(leaf, peer);
    if (identity.malformed()) {
      LOG.warn("Rejected client certificate {}: the SAN extension cannot be parsed", identity);
      countRejected(RejectionReason.MALFORMED_CERTIFICATE);
      return new Verdict(false, RejectionReason.MALFORMED_CERTIFICATE);
    }

    final var matched = matcher.match(identity);
    if (matched.isEmpty()) {
      LOG.warn(
          "Rejected client certificate {}: none of its identities {} are allowed",
          identity,
          matcher.candidates(identity).stream().map(PeerIdentity::sanitize).toList());
      countRejected(RejectionReason.IDENTITY_NOT_ALLOWED);
      return new Verdict(false, RejectionReason.IDENTITY_NOT_ALLOWED);
    }

    LOG.debug("Accepted client certificate {} matching allowed identity '{}'", identity, matched);
    Counter.builder(METRIC_PREFIX + ".accepted")
        .description("Distinct client certificates allowed, by matched identity")
        .tag("identity", matched.get())
        .register(registry)
        .increment();
    return new Verdict(true, null);
  }

  private void countRejected(final RejectionReason reason) {
    Counter.builder(METRIC_PREFIX + ".rejected")
        .description("Distinct client certificates rejected, by reason")
        .tag("reason", reason.name())
        .register(registry)
        .increment();
  }

  /** An RFC 9457 problem; it names the reason but not the allow-list. */
  private static void reject(final HttpServletResponse response, final RejectionReason reason)
      throws IOException {
    final var detail =
        reason == RejectionReason.MALFORMED_CERTIFICATE
            ? "The client certificate's subject alternative names cannot be parsed."
            : "The client certificate's identity is not allowed to access this server.";
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            """
            {"type":"about:blank","title":"Forbidden","status":403,"detail":"%s"}"""
                .formatted(detail));
  }

  private record Verdict(boolean allowed, RejectionReason reason) {}
}

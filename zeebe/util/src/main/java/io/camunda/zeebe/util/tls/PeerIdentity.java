/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.tls;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.naming.NamingException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.jspecify.annotations.Nullable;

/**
 * What a peer's leaf certificate claims to be, for logging and matching. Every field but the
 * address is attacker-controlled: {@link #toString()} escapes and truncates them for logs.
 *
 * @param subject the full subject distinguished name
 * @param subjectAlternativeNames DNS, IP, URI and email SAN entries, in certificate order
 * @param hasSubjectAlternativeNameExtension whether the certificate has a SAN extension at all
 * @param commonName the subject's CN, if it has exactly one
 * @param malformed whether the SAN extension could not be parsed
 * @param peerAddress the remote host and port, if known
 */
public record PeerIdentity(
    String subject,
    List<String> subjectAlternativeNames,
    boolean hasSubjectAlternativeNameExtension,
    @Nullable String commonName,
    boolean malformed,
    @Nullable String peerAddress) {

  private static final int SAN_EMAIL = 1;
  private static final int SAN_DNS = 2;
  private static final int SAN_URI = 6;
  private static final int SAN_IP = 7;
  private static final String SAN_EXTENSION_OID = "2.5.29.17";
  private static final int MAX_FIELD_LENGTH = 256;
  private static final int MAX_LOGGED_SANS = 10;

  /** Reads the identity from a certificate; never throws, see {@link #malformed()}. */
  public static PeerIdentity of(
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

  public PeerIdentity withPeerAddress(final @Nullable String address) {
    return new PeerIdentity(
        subject,
        subjectAlternativeNames,
        hasSubjectAlternativeNameExtension,
        commonName,
        malformed,
        address);
  }

  @Override
  public String toString() {
    final var sans = new StringBuilder("[");
    for (var i = 0; i < Math.min(subjectAlternativeNames.size(), MAX_LOGGED_SANS); i++) {
      sans.append(i == 0 ? "" : ", ").append(sanitize(subjectAlternativeNames.get(i)));
    }
    if (subjectAlternativeNames.size() > MAX_LOGGED_SANS) {
      sans.append(", ... ")
          .append(subjectAlternativeNames.size() - MAX_LOGGED_SANS)
          .append(" more");
    }
    sans.append(']');
    return "{subject='%s', sans=%s, peer=%s}"
        .formatted(sanitize(subject), sans, peerAddress == null ? "unknown" : peerAddress);
  }

  /** Escapes control and non-ASCII characters, so a certificate cannot forge log lines. */
  public static String sanitize(final String value) {
    final var out = new StringBuilder(Math.min(value.length(), MAX_FIELD_LENGTH) + 16);
    for (var i = 0; i < value.length() && out.length() < MAX_FIELD_LENGTH; i++) {
      final var c = value.charAt(i);
      if (c >= 0x20 && c < 0x7f) {
        out.append(c);
      } else {
        out.append("\\u%04x".formatted((int) c));
      }
    }
    if (out.length() >= MAX_FIELD_LENGTH) {
      out.append("...");
    }
    return out.toString();
  }
}

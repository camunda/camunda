/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.security;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.KeyManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Minimal throwaway PKI for mTLS tests: CAs, and leaf certificates with chosen SANs. */
final class TestPki {
  private static final AtomicLong SERIAL = new AtomicLong(System.currentTimeMillis());

  private TestPki() {}

  static Identity newCa(final String name) throws Exception {
    final var keys = newKeyPair();
    final var subject = new X500Name("CN=" + name);
    final var now = Instant.now();
    final var builder =
        new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(SERIAL.incrementAndGet()),
                Date.from(now.minus(Duration.ofHours(1))),
                Date.from(now.plus(Duration.ofDays(1))),
                subject,
                keys.getPublic())
            .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
            .addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
    final var certificate = sign(builder, keys);
    return new Identity(keys, certificate, null);
  }

  /** A leaf usable as both TLS client and server; SANs starting with a digit are IPs. */
  static Identity newLeaf(
      final Identity ca, final String commonName, final Instant notAfter, final String... sans)
      throws Exception {
    final var keys = newKeyPair();
    final var names = new ArrayList<GeneralName>();
    for (final var san : sans) {
      names.add(
          Character.isDigit(san.charAt(0))
              ? new GeneralName(GeneralName.iPAddress, san)
              : new GeneralName(GeneralName.dNSName, san));
    }
    final var builder =
        new JcaX509v3CertificateBuilder(
            ca.certificate(),
            BigInteger.valueOf(SERIAL.incrementAndGet()),
            Date.from(notAfter.minus(Duration.ofDays(2))),
            Date.from(notAfter),
            new X500Name("CN=" + commonName),
            keys.getPublic());
    if (!names.isEmpty()) {
      builder.addExtension(
          Extension.subjectAlternativeName,
          false,
          new GeneralNames(names.toArray(GeneralName[]::new)));
    }
    return new Identity(keys, sign(builder, ca.keys()), ca);
  }

  static Identity newLeaf(final Identity ca, final String commonName, final String... sans)
      throws Exception {
    return newLeaf(ca, commonName, Instant.now().plus(Duration.ofDays(1)), sans);
  }

  /** Writes the given CA certificates into one PEM bundle. */
  static File writeBundle(final File file, final Identity... cas) throws IOException {
    final var pem = new StringBuilder();
    for (final var ca : cas) {
      pem.append(ca.certificatePem());
    }
    Files.writeString(file.toPath(), pem, StandardCharsets.US_ASCII);
    return file;
  }

  private static KeyPair newKeyPair() throws GeneralSecurityException {
    final var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    return generator.generateKeyPair();
  }

  private static X509Certificate sign(
      final X509v3CertificateBuilder builder, final KeyPair issuerKeys) throws Exception {
    final var signer =
        new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKeys.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }

  private static String pem(final String type, final byte[] der) {
    return "-----BEGIN %s-----\n%s\n-----END %s-----\n"
        .formatted(type, Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der), type);
  }

  record Identity(KeyPair keys, X509Certificate certificate, Identity issuer) {

    String certificatePem() {
      try {
        return pem("CERTIFICATE", certificate.getEncoded());
      } catch (final GeneralSecurityException e) {
        throw new IllegalStateException(e);
      }
    }

    /** Leaf followed by its issuer, as a server presents it. */
    File writeCertificateChain(final File file) throws IOException {
      final var chain = certificatePem() + (issuer == null ? "" : issuer.certificatePem());
      Files.writeString(file.toPath(), chain, StandardCharsets.US_ASCII);
      return file;
    }

    File writePrivateKey(final File file) throws IOException {
      Files.writeString(
          file.toPath(),
          pem("PRIVATE KEY", keys.getPrivate().getEncoded()),
          StandardCharsets.US_ASCII);
      return file;
    }

    KeyManagerFactory keyManagerFactory() throws Exception {
      final var chain = new ArrayList<X509Certificate>(List.of(certificate));
      if (issuer != null) {
        chain.add(issuer.certificate());
      }
      final var keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null, null);
      keyStore.setKeyEntry(
          "identity", keys.getPrivate(), new char[0], chain.toArray(X509Certificate[]::new));
      final var factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      factory.init(keyStore, new char[0]);
      return factory;
    }
  }

  static String loopback() {
    return InetAddress.getLoopbackAddress().getHostAddress();
  }
}

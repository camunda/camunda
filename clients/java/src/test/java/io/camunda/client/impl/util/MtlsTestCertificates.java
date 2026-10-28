/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.openssl.PKCS8Generator;

/**
 * Generates the certificate fixtures used by the mTLS tests: a CA, a server certificate, and
 * client certificates (RSA and EC), plus PEM encodings of private keys in PKCS#8 (plain and
 * encrypted), PKCS#1, and SEC1 formats.
 */
public final class MtlsTestCertificates {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final long VALIDITY_MS = 60L * 60 * 1000;

  public final KeyPair caKeyPair;
  public final X509Certificate caCertificate;

  public final KeyPair serverKeyPair;
  public final X509Certificate serverCertificate;

  public final KeyPair clientKeyPair;
  public final X509Certificate clientCertificate;

  /** A client identity signed by an unrelated CA the server does not trust. */
  public final KeyPair untrustedClientKeyPair;

  public final X509Certificate untrustedClientCertificate;

  public MtlsTestCertificates() {
    this("RSA", 2048);
  }

  /** @param clientKeyAlgorithm {@code RSA} (2048) or {@code EC} (secp256r1) */
  public MtlsTestCertificates(final String clientKeyAlgorithm, final int clientKeySize) {
    try {
      caKeyPair = generateKeyPair("RSA", 2048);
      caCertificate = createCaCertificate(caKeyPair);

      serverKeyPair = generateKeyPair("RSA", 2048);
      serverCertificate =
          createLeafCertificate(
              "CN=localhost", serverKeyPair.getPublic(), caKeyPair, caCertificate);

      clientKeyPair = generateKeyPair(clientKeyAlgorithm, clientKeySize);
      clientCertificate =
          createLeafCertificate(
              "CN=camunda-client", clientKeyPair.getPublic(), caKeyPair, caCertificate);

      final KeyPair otherCaKeyPair = generateKeyPair("RSA", 2048);
      final X509Certificate otherCa = createCaCertificate(otherCaKeyPair);
      untrustedClientKeyPair = generateKeyPair("RSA", 2048);
      untrustedClientCertificate =
          createLeafCertificate(
              "CN=untrusted-client", untrustedClientKeyPair.getPublic(), otherCaKeyPair, otherCa);
    } catch (final GeneralSecurityException | OperatorCreationException | IOException e) {
      throw new RuntimeException("Failed to generate mTLS test certificates", e);
    }
  }

  private static KeyPair generateKeyPair(final String algorithm, final int keySize)
      throws GeneralSecurityException {
    final KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
    generator.initialize(keySize, RANDOM);
    return generator.generateKeyPair();
  }

  private static X509Certificate createCaCertificate(final KeyPair keyPair)
      throws GeneralSecurityException, OperatorCreationException, IOException {
    final X500Name name = new X500Name("CN=camunda-test-ca");
    final X509v3CertificateBuilder builder = certificateBuilder(name, name, keyPair.getPublic());
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    builder.addExtension(
        Extension.keyUsage,
        true,
        new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
    return sign(builder, keyPair.getPrivate(), "SHA256withRSA");
  }

  private static X509Certificate createLeafCertificate(
      final String dn,
      final PublicKey publicKey,
      final KeyPair issuerKeyPair,
      final X509Certificate issuerCertificate)
      throws GeneralSecurityException, OperatorCreationException, IOException {
    final X509v3CertificateBuilder builder =
        certificateBuilder(
            new X500Name(dn),
            new X500Name(issuerCertificate.getSubjectX500Principal().getName()),
            publicKey);
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    builder.addExtension(
        Extension.keyUsage,
        true,
        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
    final String signatureAlgorithm =
        "EC".equals(issuerKeyPair.getPrivate().getAlgorithm())
            ? "SHA256withECDSA"
            : "SHA256withRSA";
    return sign(builder, issuerKeyPair.getPrivate(), signatureAlgorithm);
  }

  private static X509v3CertificateBuilder certificateBuilder(
      final X500Name subject, final X500Name issuer, final PublicKey publicKey) {
    final long now = System.currentTimeMillis();
    return new JcaX509v3CertificateBuilder(
        issuer,
        new BigInteger(160, RANDOM),
        new Date(now - 1000),
        new Date(now + VALIDITY_MS),
        subject,
        publicKey);
  }

  private static X509Certificate sign(
      final X509v3CertificateBuilder builder,
      final PrivateKey signingKey,
      final String signatureAlgorithm)
      throws GeneralSecurityException, OperatorCreationException {
    final ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm).build(signingKey);
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }

  /** @return the PEM-encoded certificate */
  public static String toPem(final X509Certificate certificate) {
    try {
      final StringWriter writer = new StringWriter();
      try (final JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
        pemWriter.writeObject(certificate);
      }
      return writer.toString();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** @return the private key as an unencrypted PKCS#8 PEM ({@code BEGIN PRIVATE KEY}) */
  public static String toPkcs8Pem(final PrivateKey key) {
    try {
      final StringWriter writer = new StringWriter();
      try (final JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
        pemWriter.writeObject(key);
      }
      return writer.toString();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** @return the private key as an encrypted PKCS#8 PEM ({@code BEGIN ENCRYPTED PRIVATE KEY}) */
  public static String toEncryptedPkcs8Pem(final PrivateKey key, final char[] passphrase) {
    try {
      final JceOpenSSLPKCS8EncryptorBuilder encryptorBuilder =
          new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.PBE_SHA1_3DES);
      encryptorBuilder.setRandom(RANDOM);
      encryptorBuilder.setPassword(passphrase);
      final StringWriter writer = new StringWriter();
      try (final JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
        pemWriter.writeObject(
            new PKCS8Generator(
                    PrivateKeyInfo.getInstance(key.getEncoded()), encryptorBuilder.build())
                .generate());
      }
      return writer.toString();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final OperatorCreationException e) {
      throw new RuntimeException(e);
    }
  }

  /** @return the private key as a traditional unencrypted PEM (PKCS#1 RSA / SEC1 EC) */
  public static String toTraditionalPem(final PrivateKey key) {
    try {
      final StringWriter writer = new StringWriter();
      try (final JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
        pemWriter.writeObject(new JcaMiscPEMGenerator(key));
      }
      return writer.toString();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** @return a PKCS#12 keystore holding the given key and certificate chain */
  public static byte[] toPkcs12(
      final PrivateKey key,
      final X509Certificate certificate,
      final X509Certificate ca,
      final char[] password) {
    return toKeyStore("PKCS12", key, certificate, ca, password);
  }

  /** @return a JKS keystore holding the given key and certificate chain */
  public static byte[] toJks(
      final PrivateKey key,
      final X509Certificate certificate,
      final X509Certificate ca,
      final char[] password) {
    return toKeyStore("JKS", key, certificate, ca, password);
  }

  private static byte[] toKeyStore(
      final String type,
      final PrivateKey key,
      final X509Certificate certificate,
      final X509Certificate ca,
      final char[] password) {
    try {
      final KeyStore store = KeyStore.getInstance(type);
      store.load(null);
      store.setKeyEntry("client", key, password, new Certificate[] {certificate, ca});
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      store.store(out, password);
      return out.toByteArray();
    } catch (final GeneralSecurityException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** @return a PKCS#12 truststore holding the given CA certificate */
  public static byte[] toTruststore(final X509Certificate ca, final char[] password) {
    try {
      final KeyStore store = KeyStore.getInstance("PKCS12");
      store.load(null);
      store.setCertificateEntry("ca", ca);
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      store.store(out, password);
      return out.toByteArray();
    } catch (final GeneralSecurityException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}

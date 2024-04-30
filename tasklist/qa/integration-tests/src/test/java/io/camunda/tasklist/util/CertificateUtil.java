/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class CertificateUtil {

  private static final String ALGORITHM = "RSA";
  private static final int CERTIFICATE_KEY_SIZE = 2048;

  private CertificateUtil() {}

  public static void generateRSACertificate(final File certFile, final File privateKeyFile)
      throws Exception {
    final KeyPair keyPair = generateKeyPair();
    final X509Certificate cert =
        generateSelfSignedCertificate(keyPair, "CN=localhost", "SHA256withRSA");
    saveCertificateToFile(cert, certFile);
    savePrivateKeyToFile(keyPair.getPrivate(), privateKeyFile);
  }

  private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
    final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM);
    keyPairGenerator.initialize(CERTIFICATE_KEY_SIZE);
    return keyPairGenerator.generateKeyPair();
  }

  private static void saveCertificateToFile(final X509Certificate cert, final File certFile)
      throws CertificateEncodingException, IOException {
    try (final Writer writer = new FileWriter(certFile);
        final JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
      pemWriter.writeObject(cert);
    }
  }

  private static void savePrivateKeyToFile(final PrivateKey privateKey, final File privateKeyFile)
      throws IOException {
    try (final Writer writer = new FileWriter(privateKeyFile);
        final JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
      pemWriter.writeObject(privateKey);
    }
  }

  private static X509Certificate generateSelfSignedCertificate(
      final KeyPair keyPair, final String dn, final String signatureAlgorithm) throws Exception {
    final X500Name subjectName = new X500Name(dn);
    final X500Name issuerName = subjectName; // Self-signed

    final BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());
    final Instant now = Instant.now();
    final Date startDate = Date.from(now);
    final Date endDate = Date.from(now.plus(Duration.ofDays(365)));

    final JcaX509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
            issuerName, serialNumber, startDate, endDate, subjectName, keyPair.getPublic());

    final ContentSigner signer =
        new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());

    final X509CertificateHolder certHolder = certBuilder.build(signer);
    return new JcaX509CertificateConverter().getCertificate(certHolder);
  }
}

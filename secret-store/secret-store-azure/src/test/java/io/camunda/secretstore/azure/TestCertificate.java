/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.UUID;

/**
 * A self-signed certificate for {@code localhost}, generated for one test run with the JDK's
 * keytool, so no key is committed. The mocked Azure endpoints serve it, and only the test's own
 * HTTP client trusts it: the JVM's default trust store is left alone.
 */
record TestCertificate(Path keystore, String password, X509Certificate certificate) {

  private static final String ALIAS = "mock-azure";

  static TestCertificate generate(final Path directory) throws IOException, InterruptedException {
    final var keystore = directory.resolve("mock-azure.p12");
    final var password = UUID.randomUUID().toString();
    final var keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    final var process =
        new ProcessBuilder(
                keytool,
                "-genkeypair",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-alias",
                ALIAS,
                "-dname",
                "CN=localhost",
                "-ext",
                "SAN=dns:localhost,ip:127.0.0.1",
                "-validity",
                "1",
                "-storetype",
                "PKCS12",
                "-keystore",
                keystore.toString(),
                "-storepass",
                password)
            .redirectErrorStream(true)
            .start();
    final var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (process.waitFor() != 0) {
      throw new IllegalStateException("keytool failed: " + output);
    }
    return new TestCertificate(keystore, password, readCertificate(keystore, password));
  }

  private static X509Certificate readCertificate(final Path keystore, final String password)
      throws IOException {
    try (final InputStream in = Files.newInputStream(keystore)) {
      final var store = KeyStore.getInstance("PKCS12");
      store.load(in, password.toCharArray());
      return (X509Certificate) store.getCertificate(ALIAS);
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}

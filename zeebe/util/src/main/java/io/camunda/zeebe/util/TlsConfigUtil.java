/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;

public final class TlsConfigUtil {

  private TlsConfigUtil() {}

  public static void validateTlsConfig(
      final File certificateChain, final File privateKey, final File keyStore) {
    if ((certificateChain != null || privateKey != null) && keyStore != null) {
      throw new IllegalArgumentException(
          "Expected to configure with a certificate and private key pair, or with a key store and password, but both were provided. Please select only one approach");
    }

    if (keyStore == null) {
      if (certificateChain == null) {
        throw new IllegalArgumentException(
            "Expected to have a valid certificate chain path for network security, but none "
                + "configured");
      }

      if (privateKey == null) {
        throw new IllegalArgumentException(
            "Expected to have a valid private key path for network security, but none configured");
      }

      if (!certificateChain.canRead()) {
        throw new IllegalArgumentException(
            String.format(
                "Expected the configured network security certificate chain path '%s' to point to a"
                    + " readable file, but it does not",
                certificateChain));
      }

      if (!privateKey.canRead()) {
        throw new IllegalArgumentException(
            String.format(
                "Expected the configured network security private key path '%s' to point to a "
                    + "readable file, but it does not",
                privateKey));
      }
    } else {
      if (!keyStore.canRead()) {
        throw new IllegalArgumentException(
            String.format(
                "Expected the configured network security keystore file '%s' to point to a "
                    + "readable file, but it does not",
                keyStore));
      }
    }
  }

  public static PrivateKey getPrivateKey(final File keyStoreFile, final String password)
      throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
    final var sanitisedPassword = Objects.toString(password, "");
    final var keyStore = getKeyStore(keyStoreFile, sanitisedPassword);

    final String alias = keyStore.aliases().nextElement();
    return (PrivateKey) keyStore.getKey(alias, sanitisedPassword.toCharArray());
  }

  public static X509Certificate[] getCertificateChain(
      final File keyStoreFile, final String password) throws KeyStoreException {
    final var keyStore = getKeyStore(keyStoreFile, Objects.toString(password, ""));

    final String alias = keyStore.aliases().nextElement();
    return Arrays.stream(keyStore.getCertificateChain(alias))
        .map(X509Certificate.class::cast)
        .toArray(X509Certificate[]::new);
  }

  private static KeyStore getKeyStore(final File keyStoreFile, final String password)
      throws KeyStoreException {
    final var keyStore = KeyStore.getInstance("PKCS12");
    try {
      keyStore.load(new FileInputStream(keyStoreFile), password.toCharArray());
    } catch (final Exception e) {
      throw new IllegalStateException(
          String.format(
              "Keystore failed to load file: %s, please ensure it is a valid PKCS12 keystore",
              keyStoreFile.toPath()),
          e);
    }

    return keyStore;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.oauth.ClientAssertionConstants;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

public class CertificateKeyStoreLoader {

  public static PrivateKey loadPrivateKey(final OidcAuthenticationConfiguration config)
      throws KeyStoreException,
          IOException,
          NoSuchAlgorithmException,
          CertificateException,
          UnrecoverableKeyException {

    final KeyStore keystore = KeyStore.getInstance(ClientAssertionConstants.KEYSTORE_TYPE_PKCS12);
    try (final FileInputStream fis = new FileInputStream(config.getClientAssertionKeystorePath())) {
      keystore.load(fis, config.getClientAssertionKeystorePassword().toCharArray());
    }

    return (PrivateKey)
        keystore.getKey(
            config.getClientAssertionKeystoreKeyAlias(),
            config.getClientAssertionKeystoreKeyPassword().toCharArray());
  }

  public static Certificate loadCertificate(final OidcAuthenticationConfiguration config)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {

    final KeyStore keystore = KeyStore.getInstance(ClientAssertionConstants.KEYSTORE_TYPE_PKCS12);
    try (final FileInputStream fis = new FileInputStream(config.getClientAssertionKeystorePath())) {
      keystore.load(fis, config.getClientAssertionKeystorePassword().toCharArray());
    }

    return keystore.getCertificate(config.getClientAssertionKeystoreKeyAlias());
  }
}

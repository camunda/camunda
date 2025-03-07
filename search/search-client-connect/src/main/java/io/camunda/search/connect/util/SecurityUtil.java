/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.util;

import io.camunda.db.se.config.SecurityConfiguration;
import io.camunda.search.connect.SearchClientConnectException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SecurityUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityUtil.class);

  private SecurityUtil() {}

  public static SSLContext getSSLContext(
      final SecurityConfiguration configuration, final String alias)
      throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    final var trustStore = loadCustomTrustStore(configuration);

    // load custom server certificate if configured
    final var certificatePath = configuration.getCertificatePath();
    if (certificatePath != null) {
      final var certificate = loadCertificateFromPath(certificatePath);
      setCertificateInTrustStore(trustStore, certificate, alias);
    }

    final var trustStrategy =
        configuration.isSelfSigned() ? new TrustSelfSignedStrategy() : null; // default;
    if (trustStore.size() > 0) {
      return SSLContexts.custom().loadTrustMaterial(trustStore, trustStrategy).build();
    } else {
      // default if custom truststore is empty
      return SSLContext.getDefault();
    }
  }

  private static KeyStore loadCustomTrustStore(final SecurityConfiguration configuration) {
    try {
      final var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);
      return trustStore;
    } catch (final Exception e) {
      final var message =
          "Could not create certificate trustStore for the secured OpenSearch Connection!";
      throw new SearchClientConnectException(message, e);
    }
  }

  private static Certificate loadCertificateFromPath(final String certificatePath) {
    final Certificate cert;

    try (final var bis = new BufferedInputStream(new FileInputStream(certificatePath))) {
      final CertificateFactory cf = CertificateFactory.getInstance("X.509");

      if (bis.available() > 0) {
        cert = cf.generateCertificate(bis);
        LOGGER.debug("Found certificate: {}", cert);
      } else {
        throw new SearchClientConnectException(
            "Could not load certificate from file, file is empty. File: " + certificatePath);
      }
    } catch (final Exception e) {
      final String message =
          "Could not load configured server certificate for the secured Connection!";
      throw new SearchClientConnectException(message, e);
    }

    return cert;
  }

  public static void setCertificateInTrustStore(
      final KeyStore trustStore, final Certificate certificate, final String alias) {
    try {
      trustStore.setCertificateEntry(alias, certificate);
    } catch (final Exception e) {
      final String message = "Could not set configured server certificate in trust store!";
      throw new SearchClientConnectException(message, e);
    }
  }
}

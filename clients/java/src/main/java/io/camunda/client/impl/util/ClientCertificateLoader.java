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

import io.camunda.client.CamundaClientConfiguration;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.hc.core5.ssl.SSLContexts;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

/**
 * Loads mutual TLS (mTLS) material — client certificates, private keys, and CA bundles — from PEM
 * (file paths or inline content) or from PKCS#12/JKS keystores, and produces the SSL contexts used
 * by both the gRPC (Netty) and the REST (JDK) transports of the client.
 *
 * <p>Supported key formats are PKCS#8 (plain or encrypted), PKCS#1 ({@code BEGIN RSA PRIVATE KEY}),
 * and SEC1 ({@code BEGIN EC PRIVATE KEY}); parsing is backed by BouncyCastle.
 *
 * <p>Secrets (private keys, passphrases, passwords) are never logged and never included in any
 * {@code toString()} output.
 */
public final class ClientCertificateLoader {

  private ClientCertificateLoader() {}

  /**
   * @return {@code true} when any mTLS setting is present on the configuration
   */
  public static boolean isMtlsConfigured(final CamundaClientConfiguration config) {
    return isNotBlank(config.getClientCertificatePath())
        || isNotBlank(config.getClientCertificate())
        || isNotBlank(config.getClientCertificateKeyPath())
        || isNotBlank(config.getClientCertificateKey())
        || isNotBlank(config.getClientCertificateKeyPassphrase())
        || isNotBlank(config.getClientCertificateKeystorePath())
        || isNotBlank(config.getClientCertificateKeystorePassword())
        || isNotBlank(config.getClientCertificateKeystoreKeyPassword())
        || isNotBlank(config.getClientCertificateTruststorePath())
        || isNotBlank(config.getClientCertificateTruststorePassword())
        || isNotBlank(config.getClientCertificateCaPath())
        || isNotBlank(config.getClientCertificateCa());
  }

  /**
   * Validates the mTLS configuration for consistency (conflicting sources, incomplete PEM pairs,
   * plaintext addresses) without loading any material.
   *
   * @param config the client configuration
   * @param grpc whether the gRPC address should be checked; otherwise the REST address is checked
   * @throws IllegalArgumentException when the configuration is invalid
   */
  public static void validate(final CamundaClientConfiguration config, final boolean grpc) {
    if (!isMtlsConfigured(config)) {
      return;
    }

    final URI address = grpc ? config.getGrpcAddress() : config.getRestAddress();
    if (AddressUtil.isPlaintextConnection(address)) {
      throw new IllegalArgumentException(
          "Expected mTLS to be used with a TLS-enabled address, but the "
              + (grpc ? "gRPC" : "REST")
              + " address '"
              + address
              + "' uses a plaintext scheme. Use an 'https'/'grpcs' address or remove the mTLS"
              + " configuration.");
    }

    final boolean hasPemIdentity =
        isNotBlank(config.getClientCertificatePath())
            || isNotBlank(config.getClientCertificate())
            || isNotBlank(config.getClientCertificateKeyPath())
            || isNotBlank(config.getClientCertificateKey());
    final boolean hasKeystoreIdentity =
        isNotBlank(config.getClientCertificateKeystorePath())
            || isNotBlank(config.getClientCertificateKeystorePassword())
            || isNotBlank(config.getClientCertificateKeystoreKeyPassword());
    if (hasPemIdentity && hasKeystoreIdentity) {
      throw new IllegalArgumentException(
          "Expected either a PEM client certificate (clientCertificatePath/clientCertificate) or a"
              + " keystore (clientCertificateKeystorePath) as the mTLS client identity, but found"
              + " both. Configure only one of them.");
    }

    final boolean hasCert = source(config.getClientCertificatePath(), config.getClientCertificate());
    final boolean hasKey =
        source(config.getClientCertificateKeyPath(), config.getClientCertificateKey());
    if (hasCert != hasKey) {
      throw new IllegalArgumentException(
          "Expected both a client certificate and its private key for mTLS, but found only "
              + (hasCert ? "the certificate" : "the private key")
              + ". Configure both clientCertificatePath/clientCertificate and"
              + " clientCertificateKeyPath/clientCertificateKey.");
    }

    if (isNotBlank(config.getClientCertificatePath())
        && isNotBlank(config.getClientCertificate())) {
      throw new IllegalArgumentException(
          "Expected the client certificate to be configured either as a path"
              + " (clientCertificatePath) or inline (clientCertificate), but found both.");
    }
    if (isNotBlank(config.getClientCertificateKeyPath())
        && isNotBlank(config.getClientCertificateKey())) {
      throw new IllegalArgumentException(
          "Expected the client private key to be configured either as a path"
              + " (clientCertificateKeyPath) or inline (clientCertificateKey), but found both.");
    }

    final boolean hasPemCa =
        isNotBlank(config.getClientCertificateCaPath())
            || isNotBlank(config.getClientCertificateCa());
    if (isNotBlank(config.getClientCertificateCaPath())
        && isNotBlank(config.getClientCertificateCa())) {
      throw new IllegalArgumentException(
          "Expected the mTLS CA bundle to be configured either as a path (clientCertificateCaPath)"
              + " or inline (clientCertificateCa), but found both.");
    }
    final boolean hasTruststore = isNotBlank(config.getClientCertificateTruststorePath());
    if (hasPemCa && hasTruststore) {
      throw new IllegalArgumentException(
          "Expected either a PEM CA bundle (clientCertificateCaPath/clientCertificateCa) or a"
              + " truststore (clientCertificateTruststorePath) for mTLS, but found both. Configure"
              + " only one of them.");
    }
    if ((hasPemCa || hasTruststore) && isNotBlank(config.getCaCertificatePath())) {
      throw new IllegalArgumentException(
          "Expected either caCertificatePath or the mTLS CA configuration"
              + " (clientCertificateCaPath/clientCertificateCa/clientCertificateTruststorePath),"
              + " but found both. Configure only one way of trusting the cluster.");
    }
  }

  /**
   * Creates the Netty {@link SslContext} used for the gRPC transport, including the mTLS client
   * identity and CA material when configured.
   *
   * @return the context, or {@code null} when neither a CA certificate nor mTLS material is
   *     configured (the gRPC channel then uses its default SSL context)
   */
  public static SslContext createNettySslContext(final CamundaClientConfiguration config) {
    validate(config, true);
    try {
      final SSLContext sslContext = createJdkSslContext(config, true);
      if (sslContext == null) {
        return null;
      }
      return new JdkSslContext(
          sslContext,
          true,
          null,
          IdentityCipherSuiteFilter.INSTANCE,
          null,
          ClientAuth.NONE,
          null,
          false);
    } catch (final Exception e) {
      throw toRuntimeException(e);
    }
  }

  /**
   * Creates the JDK {@link SSLContext} used for the REST transport, including the mTLS client
   * identity and CA material when configured. Falls back to the default context when nothing is
   * configured.
   */
  public static SSLContext createRestSslContext(final CamundaClientConfiguration config) {
    validate(config, false);
    try {
      final SSLContext sslContext = createJdkSslContext(config, false);
      return sslContext != null ? sslContext : SSLContexts.createDefault();
    } catch (final Exception e) {
      throw toRuntimeException(e);
    }
  }

  /**
   * Builds a JDK {@link SSLContext} from the configured CA and mTLS identity material.
   *
   * @return the initialized context, or {@code null} when no CA certificate and no mTLS material is
   *     configured at all
   */
  private static SSLContext createJdkSslContext(
      final CamundaClientConfiguration config, final boolean grpc)
      throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException,
          CertificateException, UnrecoverableKeyException {
    final boolean plaintext =
        AddressUtil.isPlaintextConnection(grpc ? config.getGrpcAddress() : config.getRestAddress());

    final KeyStore trustStore = plaintext ? null : loadTrustMaterial(config);
    final KeyStore keyStore = plaintext ? null : loadKeyMaterial(config);

    if (trustStore == null && keyStore == null) {
      return null;
    }

    final TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(trustStore);

    final SSLContext context = SSLContext.getInstance("TLS");
    if (keyStore == null) {
      context.init(null, trustManagerFactory.getTrustManagers(), null);
    } else {
      final char[] keyPassword = keyPassword(config);
      final KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, keyPassword);
      context.init(
          keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
      if (keyPassword != null) {
        Arrays.fill(keyPassword, '\0');
      }
    }
    return context;
  }

  /**
   * Loads the trust material: the mTLS PEM CA bundle and/or truststore when configured, otherwise
   * the legacy {@code caCertificatePath} PEM chain.
   *
   * @return the trust store, or {@code null} when no trust material is configured
   */
  private static KeyStore loadTrustMaterial(final CamundaClientConfiguration config)
      throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
    final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(null);

    boolean hasEntries = false;
    final List<X509Certificate> caCertificates =
        readPemCertificates(config.getClientCertificateCaPath(), config.getClientCertificateCa());
    for (int i = 0; i < caCertificates.size(); i++) {
      // alias by chain index, the same technique Netty uses for PEM trust material
      trustStore.setCertificateEntry(Integer.toString(i + 1), caCertificates.get(i));
      hasEntries = true;
    }

    if (isNotBlank(config.getClientCertificateTruststorePath())) {
      final char[] truststorePassword =
          passwordChars(config.getClientCertificateTruststorePassword());
      try (final InputStream stream =
          Files.newInputStream(Paths.get(config.getClientCertificateTruststorePath()))) {
        final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(stream, truststorePassword);
        final Enumeration<String> aliases = store.aliases();
        while (aliases.hasMoreElements()) {
          final String alias = aliases.nextElement();
          trustStore.setCertificateEntry(alias, store.getCertificate(alias));
          hasEntries = true;
        }
      } finally {
        if (truststorePassword != null) {
          Arrays.fill(truststorePassword, '\0');
        }
      }
    }

    if (!hasEntries && isNotBlank(config.getCaCertificatePath())) {
      // backwards compatibility: the legacy CA certificate path keeps working as trust material
      final List<X509Certificate> legacyChain = readPemCertificates(config.getCaCertificatePath(), null);
      for (int i = 0; i < legacyChain.size(); i++) {
        trustStore.setCertificateEntry(Integer.toString(i + 1), legacyChain.get(i));
        hasEntries = true;
      }
    }

    return hasEntries ? trustStore : null;
  }

  /**
   * Loads the client identity: the PEM certificate chain + private key, or the configured keystore,
   * into a {@link KeyStore} usable with a {@link KeyManagerFactory}.
   *
   * @return the key store, or {@code null} when no client identity is configured
   */
  private static KeyStore loadKeyMaterial(final CamundaClientConfiguration config)
      throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException,
          UnrecoverableKeyException {
    if (source(config.getClientCertificatePath(), config.getClientCertificate())) {
      final List<X509Certificate> chain =
          readPemCertificates(config.getClientCertificatePath(), config.getClientCertificate());
      if (chain.isEmpty()) {
        throw new IllegalArgumentException(
            "Expected the mTLS client certificate to contain at least one certificate, but found"
                + " none.");
      }
      final PrivateKey privateKey =
          readPemPrivateKey(
              config.getClientCertificateKeyPath(),
              config.getClientCertificateKey(),
              config.getClientCertificateKeyPassphrase());
      final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null);
      keyStore.setKeyEntry("client", privateKey, new char[0], chain.toArray(new Certificate[0]));
      return keyStore;
    }

    if (isNotBlank(config.getClientCertificateKeystorePath())) {
      final char[] keystorePassword = passwordChars(config.getClientCertificateKeystorePassword());
      try (final InputStream stream =
          Files.newInputStream(Paths.get(config.getClientCertificateKeystorePath()))) {
        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(stream, keystorePassword);
        return keyStore;
      } finally {
        if (keystorePassword != null) {
          Arrays.fill(keystorePassword, '\0');
        }
      }
    }

    return null;
  }

  private static char[] keyPassword(final CamundaClientConfiguration config) {
    if (isNotBlank(config.getClientCertificateKeystorePath())) {
      // for keystores the key password defaults to the keystore password, mirroring KeyStore#load
      final String keyPassword = config.getClientCertificateKeystoreKeyPassword();
      return keyPassword != null
          ? keyPassword.toCharArray()
          : passwordChars(config.getClientCertificateKeystorePassword());
    }
    // the PEM private key was already decrypted while loading; no further password is needed
    return new char[0];
  }

  /**
   * Reads a PEM certificate chain either from a file path or from inline content.
   *
   * @return the parsed certificates, or an empty list when neither source is set
   */
  private static List<X509Certificate> readPemCertificates(final String path, final String inline)
      throws IOException, CertificateException {
    final byte[] pem = readPem(path, inline);
    if (pem == null) {
      return Collections.emptyList();
    }
    final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
    final List<X509Certificate> certificates = new ArrayList<>();
    try (final InputStream stream = new ByteArrayInputStream(pem)) {
      while (stream.available() > 0) {
        certificates.add((X509Certificate) certificateFactory.generateCertificate(stream));
      }
    }
    return certificates;
  }

  /**
   * Reads and parses a PEM private key in PKCS#8 (plain or encrypted), PKCS#1 ({@code BEGIN RSA
   * PRIVATE KEY}), or SEC1 ({@code BEGIN EC PRIVATE KEY}) format.
   */
  private static PrivateKey readPemPrivateKey(
      final String path, final String inline, final String passphrase) {
    final byte[] pem = readPem(path, inline);
    if (pem == null) {
      throw new IllegalArgumentException(
          "Expected an mTLS client private key (clientCertificateKeyPath/clientCertificateKey) but"
              + " found none.");
    }
    try (final Reader reader = new StringReader(new String(pem, StandardCharsets.US_ASCII));
        final PEMParser pemParser = new PEMParser(reader)) {
      final Object object = pemParser.readObject();
      if (object == null) {
        throw new IllegalArgumentException(
            "Expected a PEM private key but the configured key material contains no PEM object.");
      }
      final JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
      if (object instanceof PEMEncryptedKeyPair) {
        final PEMEncryptedKeyPair encryptedKeyPair = (PEMEncryptedKeyPair) object;
        // legacy OpenSSL PEM encryption (e.g. PKCS#1 / SEC1 with DEK-Info headers)
        final PEMDecryptorProvider decryptorProvider =
            new JcePEMDecryptorProviderBuilder().build(requiredPassphrase(passphrase));
        return converter
            .getKeyPair(encryptedKeyPair.decryptKeyPair(decryptorProvider))
            .getPrivate();
      }
      if (object instanceof PEMKeyPair) {
        final PEMKeyPair keyPair = (PEMKeyPair) object;
        return converter.getKeyPair(keyPair).getPrivate();
      }
      if (object instanceof PKCS8EncryptedPrivateKeyInfo) {
        final PKCS8EncryptedPrivateKeyInfo encryptedPrivateKeyInfo =
            (PKCS8EncryptedPrivateKeyInfo) object;
        final InputDecryptorProvider decryptorProvider =
            new JceOpenSSLPKCS8DecryptorProviderBuilder().build(requiredPassphrase(passphrase));
        return converter.getPrivateKey(
            encryptedPrivateKeyInfo.decryptPrivateKeyInfo(decryptorProvider));
      }
      if (object instanceof PrivateKeyInfo) {
        final PrivateKeyInfo privateKeyInfo = (PrivateKeyInfo) object;
        return converter.getPrivateKey(privateKeyInfo);
      }
      throw new IllegalArgumentException(
          "Expected a PEM private key (PKCS#8, PKCS#1, or SEC1 EC) but found an unsupported PEM"
              + " object of type "
              + object.getClass().getSimpleName());
    } catch (final IOException | OperatorCreationException | PKCSException e) {
      throw new RuntimeException("Failed to read the mTLS client private key", e);
    }
  }

  private static char[] requiredPassphrase(final String passphrase) {
    if (passphrase == null || passphrase.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected a passphrase (clientCertificateKeyPassphrase) for the encrypted mTLS client"
              + " private key, but found none.");
    }
    return passphrase.toCharArray();
  }

  /**
   * @return the PEM bytes from the given file path or inline content, or {@code null} when neither
   *     is set
   */
  private static byte[] readPem(final String path, final String inline) {
    try {
      if (isNotBlank(path)) {
        return Files.readAllBytes(Paths.get(path));
      }
      if (isNotBlank(inline)) {
        return inline.getBytes(StandardCharsets.US_ASCII);
      }
      return null;
    } catch (final IOException e) {
      throw new RuntimeException("Failed to read mTLS PEM material from '" + path + "'", e);
    }
  }

  private static boolean source(final String path, final String inline) {
    return isNotBlank(path) || isNotBlank(inline);
  }

  private static boolean isNotBlank(final String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static char[] passwordChars(final String password) {
    return password != null ? password.toCharArray() : null;
  }

  private static RuntimeException toRuntimeException(final Exception e) {
    if (e instanceof RuntimeException) {
      return (RuntimeException) e;
    }
    return new RuntimeException("Failed to configure mTLS for the Camunda client", e);
  }
}

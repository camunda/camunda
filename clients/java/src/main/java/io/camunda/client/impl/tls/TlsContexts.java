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
package io.camunda.client.impl.tls;

import io.camunda.client.CamundaClientConfiguration;
import io.grpc.netty.GrpcSslContexts;
import io.netty.handler.ssl.SslContext;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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
 * Shared certificate loader for mutual TLS against the Camunda cluster. From one {@link
 * MtlsMaterial} it produces both:
 *
 * <ul>
 *   <li>a Netty {@link SslContext} for the gRPC channel ({@link #createGrpcSslContext}), and
 *   <li>a JDK {@link SSLContext} for the REST client ({@link #createRestSslContext}).
 * </ul>
 *
 * <p>Supported key formats: PKCS#8 (encrypted or unencrypted), PKCS#1 ({@code BEGIN RSA PRIVATE
 * KEY}), and SEC-1 EC keys ({@code BEGIN EC PRIVATE KEY}), parsed with BouncyCastle. Keystores may
 * be PKCS#12 or JKS (selected by file extension: {@code .p12}/{@code .pfx} are PKCS#12, everything
 * else JKS).
 *
 * <p>The mTLS material applies to the cluster connection only. It is never reused for the OAuth
 * token endpoint, and the OAuth keystore ({@code CAMUNDA_SSL_CLIENT_KEYSTORE_*}) is never reused
 * for the cluster connection.
 */
public final class TlsContexts {

  static {
    // Required to decrypt encrypted PEM private keys (PBKDF/OpenSSL PBDKF and AES are not
    // guaranteed to be provided by the JVM). Registered once, defensively.
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private TlsContexts() {}

  /**
   * Builds the Netty {@link SslContext} for the gRPC channel, or {@code null} when no TLS
   * customization is configured (no mTLS material and no legacy CA certificate), in which case the
   * gRPC defaults apply.
   */
  public static SslContext createGrpcSslContext(final CamundaClientConfiguration config) {
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);
    if (!material.isConfigured() && material.getLegacyCaCertificatePath() == null) {
      return null;
    }

    try {
      final io.netty.handler.ssl.SslContextBuilder builder = GrpcSslContexts.forClient();
      final TrustMaterial trust = loadTrustMaterial(material);
      if (trust != null) {
        builder.trustManager(trust.toTrustManagerFactory());
      }
      final KeyMaterial identity = loadKeyMaterial(material);
      if (identity != null) {
        final KeyMaterial unpacked = identity.unpacked();
        builder.keyManager(unpacked.privateKey, unpacked.certificateChain);
      }
      return builder.build();
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to configure TLS for the gRPC connection", e);
    }
  }

  /**
   * Builds the JDK {@link SSLContext} for the REST client. Falls back to the default context when
   * no TLS customization is configured.
   */
  public static SSLContext createRestSslContext(final CamundaClientConfiguration config) {
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);
    if (!material.isConfigured() && material.getLegacyCaCertificatePath() == null) {
      return defaultContext();
    }

    try {
      final SSLContext context = SSLContext.getInstance("TLS");
      final TrustMaterial trust = loadTrustMaterial(material);
      final KeyMaterial identity = loadKeyMaterial(material);
      context.init(
          identity == null ? null : identity.toKeyManagerFactory().getKeyManagers(),
          trust == null ? null : trust.toTrustManagerFactory().getTrustManagers(),
          null);
      return context;
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to configure TLS for the REST connection", e);
    }
  }

  private static SSLContext defaultContext() {
    try {
      return SSLContext.getDefault();
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("Failed to create the default SSL context", e);
    }
  }

  /**
   * Loads the certificates the client trusts, from (in order of precedence, exclusive by
   * validation): an mTLS truststore, an mTLS CA bundle (PEM), or the legacy {@code
   * caCertificatePath} (PEM). Returns {@code null} when none is configured (JVM default trust
   * applies).
   */
  private static TrustMaterial loadTrustMaterial(final MtlsMaterial material)
      throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
    if (material.getTruststorePath() != null) {
      final KeyStore truststore =
          loadKeystore(material.getTruststorePath(), material.getTruststorePassword());
      return new TrustMaterial(truststore);
    }
    if (material.getCaPath() != null || material.getCa() != null) {
      return new TrustMaterial(
          pemCertificatesToKeyStore(readPem(material.getCaPath(), material.getCa())));
    }
    if (material.getLegacyCaCertificatePath() != null) {
      try (final InputStream stream = new FileInputStream(material.getLegacyCaCertificatePath())) {
        return new TrustMaterial(pemCertificatesToKeyStore(stream));
      }
    }
    return null;
  }

  /**
   * Loads the client identity (private key and certificate chain), from either the PEM material or
   * the keystore. Returns {@code null} when no identity is configured (plain one-way TLS).
   */
  private static KeyMaterial loadKeyMaterial(final MtlsMaterial material)
      throws IOException,
          OperatorCreationException,
          PKCSException,
          CertificateException,
          KeyStoreException,
          NoSuchAlgorithmException,
          UnrecoverableKeyException {
    if (material.getKeystorePath() != null) {
      return KeyMaterial.fromKeystore(
          loadKeystore(material.getKeystorePath(), material.getKeystorePassword()),
          material.getKeystoreKeyPassword() != null
              ? material.getKeystoreKeyPassword()
              : material.getKeystorePassword());
    }
    if (material.hasPemIdentity()) {
      final List<X509Certificate> chain =
          parsePemCertificates(readPem(material.getCertPath(), material.getCert()));
      final PrivateKey privateKey =
          parsePemPrivateKey(
              readPem(material.getKeyPath(), material.getKey()), material.getKeyPassphrase());
      return new KeyMaterial(privateKey, chain.toArray(new X509Certificate[0]));
    }
    return null;
  }

  private static InputStream readPem(final String path, final String inline) throws IOException {
    if (inline != null) {
      return new ByteArrayInputStream(inline.getBytes(StandardCharsets.UTF_8));
    }
    return new FileInputStream(path);
  }

  static List<X509Certificate> parsePemCertificates(final InputStream stream)
      throws CertificateException, IOException {
    final List<X509Certificate> certificates = new ArrayList<>();
    final CertificateFactory factory = CertificateFactory.getInstance("X.509");
    try (final InputStream in = stream) {
      while (in.available() > 0) {
        certificates.add((X509Certificate) factory.generateCertificate(in));
      }
    }
    return certificates;
  }

  /**
   * Parses a PEM private key: PKCS#8 (encrypted or unencrypted), PKCS#1 ({@code BEGIN RSA PRIVATE
   * KEY}), or SEC-1 EC ({@code BEGIN EC PRIVATE KEY}).
   */
  static PrivateKey parsePemPrivateKey(final InputStream stream, final String passphrase)
      throws IOException, OperatorCreationException, PKCSException {
    final Object parsed;
    try (final Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        final PEMParser pemParser = new PEMParser(reader)) {
      parsed = pemParser.readObject();
      if (parsed == null) {
        throw new IllegalArgumentException(
            "The mTLS private key PEM does not contain a key (expected PKCS#8, PKCS#1, or EC)");
      }
    }

    final JcaPEMKeyConverter converter =
        new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
    if (parsed instanceof PKCS8EncryptedPrivateKeyInfo) {
      if (passphrase == null) {
        throw new IllegalArgumentException(
            "The mTLS private key is encrypted but no passphrase is configured "
                + "(CAMUNDA_MTLS_KEY_PASSPHRASE)");
      }
      final InputDecryptorProvider decryptor =
          new JceOpenSSLPKCS8DecryptorProviderBuilder()
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .build(passphrase.toCharArray());
      return converter.getPrivateKey(
          ((PKCS8EncryptedPrivateKeyInfo) parsed).decryptPrivateKeyInfo(decryptor));
    }
    if (parsed instanceof PrivateKeyInfo) {
      return converter.getPrivateKey((PrivateKeyInfo) parsed);
    }
    if (parsed instanceof PEMEncryptedKeyPair) {
      if (passphrase == null) {
        throw new IllegalArgumentException(
            "The mTLS private key is encrypted but no passphrase is configured "
                + "(CAMUNDA_MTLS_KEY_PASSPHRASE)");
      }
      return converter
          .getKeyPair(
              ((PEMEncryptedKeyPair) parsed)
                  .decryptKeyPair(
                      new JcePEMDecryptorProviderBuilder()
                          .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                          .build(passphrase.toCharArray())))
          .getPrivate();
    }
    if (parsed instanceof PEMKeyPair) {
      return converter.getKeyPair((PEMKeyPair) parsed).getPrivate();
    }
    throw new IllegalArgumentException(
        "Unsupported mTLS private key PEM content of type "
            + parsed.getClass().getName()
            + " (expected PKCS#8, PKCS#1, or EC)");
  }

  /** Loads a PKCS#12 ({@code .p12}/{@code .pfx}) or JKS keystore, chosen by file extension. */
  static KeyStore loadKeystore(final String path, final String password)
      throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
    final String lower = path.toLowerCase();
    final String type = lower.endsWith(".p12") || lower.endsWith(".pfx") ? "PKCS12" : "JKS";
    final KeyStore store = KeyStore.getInstance(type);
    try (final InputStream stream = new FileInputStream(path)) {
      store.load(stream, password == null ? null : password.toCharArray());
    }
    return store;
  }

  /**
   * Loads a PEM certificate chain into a key store, with the alias set to the index of the
   * certificate in the chain — the same technique Netty uses for the gRPC API, and backwards
   * compatible with how the legacy {@code caCertificatePath} was handled for REST.
   */
  private static KeyStore pemCertificatesToKeyStore(final InputStream stream)
      throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
    final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
    store.load(null);
    int i = 1;
    for (final X509Certificate certificate : parsePemCertificates(stream)) {
      store.setCertificateEntry(Integer.toString(i), certificate);
      i++;
    }
    return store;
  }

  private static final class TrustMaterial {
    private final KeyStore truststore;

    private TrustMaterial(final KeyStore truststore) {
      this.truststore = truststore;
    }

    private TrustManagerFactory toTrustManagerFactory()
        throws NoSuchAlgorithmException, KeyStoreException {
      final TrustManagerFactory factory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init(truststore);
      return factory;
    }
  }

  private static final class KeyMaterial {
    private final PrivateKey privateKey;
    private final X509Certificate[] certificateChain;
    private final KeyStore keystore;
    private final String keyPassword;

    private KeyMaterial(final PrivateKey privateKey, final X509Certificate[] certificateChain) {
      this.privateKey = privateKey;
      this.certificateChain = certificateChain;
      keystore = null;
      keyPassword = null;
    }

    private KeyMaterial(final KeyStore keystore, final String keyPassword) {
      this.keystore = keystore;
      this.keyPassword = keyPassword;
      privateKey = null;
      certificateChain = null;
    }

    private static KeyMaterial fromKeystore(final KeyStore keystore, final String keyPassword)
        throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
      // Fail fast when the keystore holds no usable private key entry, instead of failing later
      // with an opaque TLS handshake error.
      final char[] passwordChars = keyPassword == null ? new char[0] : keyPassword.toCharArray();
      final Enumeration<String> aliases = keystore.aliases();
      while (aliases.hasMoreElements()) {
        final String alias = aliases.nextElement();
        if (keystore.isKeyEntry(alias)) {
          keystore.getKey(alias, passwordChars);
          return new KeyMaterial(keystore, keyPassword);
        }
      }
      throw new IllegalArgumentException(
          "The mTLS keystore does not contain a private key entry for the client identity");
    }

    /**
     * Unpacks the identity into a private key and certificate chain, the form Netty's {@code
     * keyManager} needs regardless of the TLS provider in use (the JDK engine also accepts a
     * KeyManagerFactory, but unpacking keeps the gRPC setup identical for PEM and keystore inputs).
     */
    private KeyMaterial unpacked()
        throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
      if (keystore == null) {
        return this;
      }
      final char[] passwordChars = keyPassword == null ? new char[0] : keyPassword.toCharArray();
      final Enumeration<String> aliases = keystore.aliases();
      while (aliases.hasMoreElements()) {
        final String alias = aliases.nextElement();
        if (keystore.isKeyEntry(alias)) {
          final PrivateKey key = (PrivateKey) keystore.getKey(alias, passwordChars);
          final Certificate[] chain = keystore.getCertificateChain(alias);
          final List<X509Certificate> x509Chain = new ArrayList<>();
          if (chain != null) {
            for (final Certificate certificate : chain) {
              x509Chain.add((X509Certificate) certificate);
            }
          }
          return new KeyMaterial(key, x509Chain.toArray(new X509Certificate[0]));
        }
      }
      throw new IllegalArgumentException(
          "The mTLS keystore does not contain a private key entry for the client identity");
    }

    private KeyManagerFactory toKeyManagerFactory()
        throws NoSuchAlgorithmException,
            KeyStoreException,
            UnrecoverableKeyException,
            CertificateException,
            IOException {
      if (keystore != null) {
        final KeyManagerFactory factory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keystore, keyPassword == null ? new char[0] : keyPassword.toCharArray());
        return factory;
      }
      final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
      store.load(null);
      store.setKeyEntry(
          "mtls-client",
          privateKey,
          new char[0],
          certificateChain == null ? new Certificate[0] : certificateChain);
      final KeyManagerFactory factory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      factory.init(store, new char[0]);
      return factory;
    }
  }
}

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
package io.camunda.client.impl.oauth;

import static java.lang.Math.toIntExact;
import static java.util.UUID.randomUUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.CamundaClientCredentials;
import io.camunda.client.impl.util.VersionUtil;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import net.jcip.annotations.ThreadSafe;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is thread-safe in terms of the next: 1. If you are trying to modify headers of your
 * request from the several threads there would be sequential calls to the cache 2. If the cache
 * hasn't a valid token and you are calling from several threads there would be just one call to the
 * Auth server
 */
@ThreadSafe
public final class OAuthCredentialsProvider implements CredentialsProvider {
  private static final String HEADER_AUTH_KEY = "Authorization";
  private static final String JWT_ASSERTION_TYPE =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

  private static final ObjectMapper JSON_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final ObjectReader CREDENTIALS_READER =
      JSON_MAPPER.readerFor(CamundaClientCredentials.class);
  private static final Logger LOG = LoggerFactory.getLogger(OAuthCredentialsProvider.class);
  private final URL authorizationServerUrl;
  private final String clientId;
  private final String clientSecret;
  private final String audience;
  private final String scope;
  private final String resource;
  private final Path keystorePath;
  private final String keystorePassword;
  private final String keystoreKeyPassword;
  private final Path truststorePath;
  private final String truststorePassword;
  private final OAuthCredentialsCache credentialsCache;
  private final Duration connectionTimeout;
  private final Duration readTimeout;
  // client assertion
  private final boolean clientAssertionEnabled;
  private final Path clientAssertionKeystorePath;
  private final String clientAssertionKeystorePassword;
  private final String clientAssertionKeystoreKeyAlias;
  private final String clientAssertionKeystoreKeyPassword;

  OAuthCredentialsProvider(final OAuthCredentialsProviderBuilder builder) {
    authorizationServerUrl = builder.getAuthorizationServer();
    keystorePath = builder.getKeystorePath();
    keystorePassword = builder.getKeystorePassword();
    keystoreKeyPassword = builder.getKeystoreKeyPassword();
    truststorePath = builder.getTruststorePath();
    truststorePassword = builder.getTruststorePassword();
    clientId = builder.getClientId();
    clientSecret = builder.getClientSecret();
    audience = builder.getAudience();
    scope = builder.getScope();
    resource = builder.getResource();
    credentialsCache = new OAuthCredentialsCache(builder.getCredentialsCache());
    connectionTimeout = builder.getConnectTimeout();
    readTimeout = builder.getReadTimeout();
    clientAssertionEnabled = builder.clientAssertionEnabled();
    clientAssertionKeystorePath = builder.getClientAssertionKeystorePath();
    clientAssertionKeystorePassword = builder.getClientAssertionKeystorePassword();
    clientAssertionKeystoreKeyAlias = builder.getClientAssertionKeystoreKeyAlias();
    clientAssertionKeystoreKeyPassword = builder.getClientAssertionKeystoreKeyPassword();
  }

  public boolean isClientAssertionEnabled() {
    return clientAssertionEnabled;
  }

  /** Adds an access token to the Authorization header of a gRPC call. */
  @Override
  public void applyCredentials(final CredentialsApplier applier) throws IOException {
    final CamundaClientCredentials camundaClientCredentials =
        credentialsCache.computeIfMissingOrInvalid(clientId, this::fetchCredentials);

    String type = camundaClientCredentials.getTokenType();
    if (type == null || type.isEmpty()) {
      throw new IOException(
          String.format("Expected valid token type but was absent or invalid '%s'", type));
    }

    type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
    applier.put(
        HEADER_AUTH_KEY, String.format("%s %s", type, camundaClientCredentials.getAccessToken()));
  }

  /**
   * Returns true if the request failed because it was unauthenticated or unauthorized, and a new
   * access token could be fetched; otherwise returns false.
   */
  @Override
  public boolean shouldRetryRequest(final StatusCode statusCode) {
    try {
      return statusCode.isUnauthorized()
          && credentialsCache
              .withCache(
                  clientId,
                  value -> {
                    final CamundaClientCredentials fetchedCredentials = fetchCredentials();
                    credentialsCache.put(clientId, fetchedCredentials).writeCache();
                    return !fetchedCredentials.equals(value);
                  })
              .orElse(false);
    } catch (final IOException e) {
      LOG.error("Failed while fetching credentials: ", e);
      return false;
    }
  }

  private String createPayload() {
    final Map<String, String> payload = new HashMap<>();
    if (clientAssertionEnabled) {
      payload.put("client_assertion", getClientAssertion());
      payload.put("client_assertion_type", JWT_ASSERTION_TYPE);
    } else {
      payload.put("client_secret", clientSecret);
    }

    payload.put("client_id", clientId);
    payload.put("audience", audience);
    payload.put("grant_type", "client_credentials");
    if (scope != null) {
      payload.put("scope", scope);
    }
    if (resource != null) {
      payload.put("resource", resource);
    }

    return payload.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String encode(final String param) {
    try {
      return URLEncoder.encode(param, StandardCharsets.UTF_8.name());
    } catch (final UnsupportedEncodingException e) {
      throw new UncheckedIOException("Failed while encoding OAuth request parameters: ", e);
    }
  }

  private CamundaClientCredentials fetchCredentials() throws IOException {
    final HttpURLConnection connection =
        (HttpURLConnection) authorizationServerUrl.openConnection();
    maybeConfigureCustomSSLContext(connection);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    connection.setRequestProperty("Accept", "application/json");
    connection.setDoOutput(true);
    connection.setReadTimeout(toIntExact(readTimeout.toMillis()));
    connection.setConnectTimeout(toIntExact(connectionTimeout.toMillis()));
    connection.setRequestProperty("User-Agent", "camunda-client-java/" + VersionUtil.getVersion());

    try (final OutputStream os = connection.getOutputStream()) {
      final byte[] input = createPayload().getBytes(StandardCharsets.UTF_8);
      os.write(input, 0, input.length);
    }

    if (connection.getResponseCode() != 200) {
      throw new IOException(
          String.format(
              "Failed while requesting access token with status code %d and message %s.",
              connection.getResponseCode(), connection.getResponseMessage()));
    }

    try (final InputStream in = connection.getInputStream();
        final InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
      final CamundaClientCredentials fetchedCredentials = CREDENTIALS_READER.readValue(reader);

      if (fetchedCredentials == null) {
        throw new IOException("Expected valid credentials but got null instead.");
      }

      return fetchedCredentials;
    }
  }

  private void maybeConfigureCustomSSLContext(final HttpURLConnection connection) {
    if (connection instanceof HttpsURLConnection) {
      final HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
      httpsConnection.setSSLSocketFactory(createSSLContext());
    }
  }

  private SSLSocketFactory createSSLContext() {
    if (keystorePath == null && truststorePath == null) {
      return SSLContexts.createSystemDefault().getSocketFactory();
    }
    final SSLContextBuilder builder = SSLContexts.custom();
    try {
      if (keystorePath != null) {
        builder.loadKeyMaterial(
            keystorePath,
            keystorePassword == null ? null : keystorePassword.toCharArray(),
            keystoreKeyPassword == null ? new char[0] : keystoreKeyPassword.toCharArray());
      }
      if (truststorePath != null) {
        builder.loadTrustMaterial(
            truststorePath, truststorePassword == null ? null : truststorePassword.toCharArray());
      }
      return builder.build().getSocketFactory();
    } catch (final NoSuchAlgorithmException
        | KeyManagementException
        | KeyStoreException
        | UnrecoverableKeyException
        | CertificateException
        | IOException e) {
      throw new RuntimeException("Failed to create SSL context", e);
    }
  }

  private String getClientAssertion() {
    final X509Certificate certificate;
    final Algorithm algorithm;
    try (final FileInputStream stream = new FileInputStream(clientAssertionKeystorePath.toFile())) {
      final KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(stream, clientAssertionKeystorePassword.toCharArray());

      final RSAPrivateKey privateKey =
          (RSAPrivateKey)
              keyStore.getKey(
                  clientAssertionKeystoreKeyAlias,
                  clientAssertionKeystoreKeyPassword.toCharArray());
      final X509Certificate keyStoreCertificate =
          (X509Certificate) keyStore.getCertificate(clientAssertionKeystoreKeyAlias);
      final RSAPublicKey publicKey = (RSAPublicKey) keyStoreCertificate.getPublicKey();

      certificate = (X509Certificate) keyStore.getCertificate(clientAssertionKeystoreKeyAlias);
      algorithm = Algorithm.RSA256(publicKey, privateKey);
    } catch (final IOException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to create client assertion", e);
    }

    final Date now = new Date();
    final String x5t = generateX5tThumbprint(certificate);

    final Map<String, Object> header = new HashMap<>();
    header.put("alg", ClientAssertionConstants.JWT_ALGORITHM_RS256);
    header.put("typ", ClientAssertionConstants.JWT_TYPE);
    header.put("x5t", x5t);

    return JWT.create()
        .withHeader(header)
        .withIssuer(clientId)
        .withSubject(clientId)
        .withAudience(authorizationServerUrl.toString())
        .withIssuedAt(now)
        .withNotBefore(now)
        .withExpiresAt(new Date(now.getTime() + 5 * 60 * 1000))
        .withJWTId(randomUUID().toString())
        .sign(algorithm);
  }

  private static String generateX5tThumbprint(final X509Certificate certificate) {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-1");
      final byte[] encoded = digest.digest(certificate.getEncoded());
      return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to generate x5t thumbprint", e);
    }
  }
}

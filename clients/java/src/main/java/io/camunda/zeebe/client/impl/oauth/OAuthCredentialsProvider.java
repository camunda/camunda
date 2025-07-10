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
package io.camunda.zeebe.client.impl.oauth;

import static java.lang.Math.toIntExact;
import static java.util.UUID.randomUUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.impl.ZeebeClientCredentials;
import io.camunda.zeebe.client.impl.util.VersionUtil;
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
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.jcip.annotations.ThreadSafe;
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

  private static final ObjectMapper JSON_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final ObjectReader CREDENTIALS_READER =
      JSON_MAPPER.readerFor(ZeebeClientCredentials.class);
  private static final Logger LOG = LoggerFactory.getLogger(OAuthCredentialsProvider.class);
  private final URL authorizationServerUrl;
  private final String payload;
  private final String clientId;
  private final OAuthCredentialsCache credentialsCache;
  private final Duration connectionTimeout;
  private final Duration readTimeout;

  OAuthCredentialsProvider(final OAuthCredentialsProviderBuilder builder) {
    authorizationServerUrl = builder.getAuthorizationServer();
    clientId = builder.getClientId();
    payload = createParams(builder);
    credentialsCache = new OAuthCredentialsCache(builder.getCredentialsCache());
    connectionTimeout = builder.getConnectTimeout();
    readTimeout = builder.getReadTimeout();
  }

  /** Adds an access token to the Authorization header of a gRPC call. */
  @Override
  public void applyCredentials(final CredentialsApplier applier) throws IOException {
    final ZeebeClientCredentials zeebeClientCredentials =
        credentialsCache.computeIfMissingOrInvalid(clientId, this::fetchCredentials);

    String type = zeebeClientCredentials.getTokenType();
    if (type == null || type.isEmpty()) {
      throw new IOException(
          String.format("Expected valid token type but was absent or invalid '%s'", type));
    }

    type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
    applier.put(
        HEADER_AUTH_KEY, String.format("%s %s", type, zeebeClientCredentials.getAccessToken()));
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
                    final ZeebeClientCredentials fetchedCredentials = fetchCredentials();
                    credentialsCache.put(clientId, fetchedCredentials).writeCache();
                    return !fetchedCredentials.equals(value);
                  })
              .orElse(false);
    } catch (final IOException e) {
      LOG.error("Failed while fetching credentials: ", e);
      return false;
    }
  }

  private static String createParams(final OAuthCredentialsProviderBuilder builder) {
    final Map<String, String> payload = new HashMap<>();
    payload.put("client_id", builder.getClientId());
    if (builder.clientAssertionEnabled()) {
      payload.put(ClientAssertionConstants.CLIENT_ASSERTION_PARAM, getClientAssertion(builder));
      payload.put(
          ClientAssertionConstants.CLIENT_ASSERTION_TYPE_PARAM,
          ClientAssertionConstants.CLIENT_ASSERTION_TYPE_JWT_BEARER);
    } else {
      payload.put("client_secret", builder.getClientSecret());
    }

    payload.put("audience", builder.getAudience());
    payload.put("grant_type", "client_credentials");
    final String scope = builder.getScope();
    if (scope != null) {
      payload.put("scope", scope);
    }
    final String resource = builder.getResource();
    if (resource != null) {
      payload.put("resource", resource);
    }

    return payload.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String getClientAssertion(final OAuthCredentialsProviderBuilder builder) {
    final X509Certificate certificate;
    final Algorithm algorithm;
    try (final FileInputStream stream =
        new FileInputStream(builder.getClientAssertionKeystorePath().toFile())) {
      final KeyStore keyStore = KeyStore.getInstance(ClientAssertionConstants.KEYSTORE_TYPE_PKCS12);
      keyStore.load(stream, builder.getClientAssertionKeystorePassword().toCharArray());

      final RSAPrivateKey privateKey =
          (RSAPrivateKey)
              keyStore.getKey(
                  builder.getClientAssertionKeystoreKeyAlias(),
                  builder.getClientAssertionKeystoreKeyPassword().toCharArray());
      final X509Certificate keyStoreCertificate =
          (X509Certificate) keyStore.getCertificate(builder.getClientAssertionKeystoreKeyAlias());
      final RSAPublicKey publicKey = (RSAPublicKey) keyStoreCertificate.getPublicKey();

      certificate =
          (X509Certificate) keyStore.getCertificate(builder.getClientAssertionKeystoreKeyAlias());
      algorithm = Algorithm.RSA256(publicKey, privateKey);
    } catch (final IOException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to create client assertion", e);
    }

    final String x5t = generateX5tThumbprint(certificate);

    final Map<String, Object> header = new HashMap<>();
    header.put(
        ClientAssertionConstants.JWT_HEADER_ALG, ClientAssertionConstants.JWT_ALGORITHM_RS256);
    header.put(ClientAssertionConstants.JWT_HEADER_TYP, ClientAssertionConstants.JWT_TYPE);
    header.put(ClientAssertionConstants.JWT_HEADER_X5T, x5t);

    final ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
    return JWT.create()
        .withHeader(header)
        .withIssuer(builder.getClientId())
        .withSubject(builder.getClientId())
        .withAudience(builder.getAuthorizationServer().toString())
        .withIssuedAt(now.toInstant())
        .withNotBefore(now.toInstant())
        .withExpiresAt(now.plusMinutes(5L).toInstant())
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

  private static String encode(final String param) {
    try {
      return URLEncoder.encode(param, StandardCharsets.UTF_8.name());
    } catch (final UnsupportedEncodingException e) {
      throw new UncheckedIOException("Failed while encoding OAuth request parameters: ", e);
    }
  }

  private ZeebeClientCredentials fetchCredentials() throws IOException {
    final HttpURLConnection connection =
        (HttpURLConnection) authorizationServerUrl.openConnection();
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    connection.setRequestProperty("Accept", "application/json");
    connection.setDoOutput(true);
    connection.setReadTimeout(toIntExact(readTimeout.toMillis()));
    connection.setConnectTimeout(toIntExact(connectionTimeout.toMillis()));
    connection.setRequestProperty("User-Agent", "zeebe-client-java/" + VersionUtil.getVersion());

    try (final OutputStream os = connection.getOutputStream()) {
      final byte[] input = payload.getBytes(StandardCharsets.UTF_8);
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
      final ZeebeClientCredentials fetchedCredentials = CREDENTIALS_READER.readValue(reader);

      if (fetchedCredentials == null) {
        throw new IOException("Expected valid credentials but got null instead.");
      }

      return fetchedCredentials;
    }
  }
}

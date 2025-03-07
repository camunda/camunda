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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.CamundaClientCredentials;
import io.camunda.client.impl.util.VersionUtil;
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
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Duration;
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

  private static final ObjectMapper JSON_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final ObjectReader CREDENTIALS_READER =
      JSON_MAPPER.readerFor(CamundaClientCredentials.class);
  private static final Logger LOG = LoggerFactory.getLogger(OAuthCredentialsProvider.class);
  private final URL authorizationServerUrl;
  private final String payload;
  private final String clientId;
  private final Path keystorePath;
  private final String keystorePassword;
  private final String keystoreKeyPassword;
  private final Path truststorePath;
  private final String truststorePassword;
  private final OAuthCredentialsCache credentialsCache;
  private final Duration connectionTimeout;
  private final Duration readTimeout;

  OAuthCredentialsProvider(final OAuthCredentialsProviderBuilder builder) {
    authorizationServerUrl = builder.getAuthorizationServer();
    keystorePath = builder.getKeystorePath();
    keystorePassword = builder.getKeystorePassword();
    keystoreKeyPassword = builder.getKeystoreKeyPassword();
    truststorePath = builder.getTruststorePath();
    truststorePassword = builder.getTruststorePassword();
    clientId = builder.getClientId();
    payload = createParams(builder);
    credentialsCache = new OAuthCredentialsCache(builder.getCredentialsCache());
    connectionTimeout = builder.getConnectTimeout();
    readTimeout = builder.getReadTimeout();
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
                    return !fetchedCredentials.equals(value) || !value.isValid();
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
    payload.put("client_secret", builder.getClientSecret());
    payload.put("audience", builder.getAudience());
    payload.put("grant_type", "client_credentials");
    final String scope = builder.getScope();
    if (scope != null && !scope.isEmpty()) {
      payload.put("scope", scope);
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
}

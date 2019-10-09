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
package io.zeebe.client.impl.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.io.CharStreams;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.zeebe.client.CredentialsProvider;
import io.zeebe.client.impl.ZeebeClientCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAuthCredentialsProvider implements CredentialsProvider {
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final ObjectReader CREDENTIALS_READER =
      JSON_MAPPER.readerFor(ZeebeClientCredentials.class);
  private static final Logger LOG = LoggerFactory.getLogger(OAuthCredentialsProvider.class);
  private static final Key<String> HEADER_AUTH_KEY =
      Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final URL authorizationServerUrl;
  private final String jsonPayload;
  private final String endpoint;
  private final OAuthCredentialsCache credentialsCache;

  private ZeebeClientCredentials credentials;

  OAuthCredentialsProvider(final OAuthCredentialsProviderBuilder builder) {
    authorizationServerUrl = builder.getAuthorizationServer();
    endpoint = builder.getAudience();
    jsonPayload = createJsonPayload(builder);
    credentialsCache = new OAuthCredentialsCache(builder.getCredentialsCache());
  }

  /** Adds an access token to the Authorization header of a gRPC call. */
  @Override
  public void applyCredentials(final Metadata headers) {
    try {
      if (credentials == null) {
        loadCredentials();
      }

      headers.put(
          HEADER_AUTH_KEY,
          String.format("%s %s", credentials.getTokenType(), credentials.getAccessToken()));
    } catch (final IOException e) {
      LOG.warn("Failed while fetching credentials, will not add credentials to rpc: ", e);
    }
  }

  /**
   * Returns true if the Throwable was caused by an UNAUTHENTICATED response and a new access token
   * could be fetched; otherwise returns false.
   */
  @Override
  public boolean shouldRetryRequest(final Throwable throwable) {
    try {
      return throwable instanceof StatusRuntimeException
          && ((StatusRuntimeException) throwable).getStatus() == Status.UNAUTHENTICATED
          && refreshCredentials();
    } catch (final IOException e) {
      LOG.error("Failed while fetching credentials: ", e);
      return false;
    }
  }

  /** Attempt to load credentials from cache and, if unsuccessful, fetch new credentials. */
  private void loadCredentials() throws IOException {
    Optional<ZeebeClientCredentials> cachedCredentials;

    try {
      cachedCredentials = credentialsCache.readCache().get(endpoint);
    } catch (final IOException e) {
      LOG.debug("Failed to read credentials cache", e);
      cachedCredentials = Optional.empty();
    }

    if (cachedCredentials.isPresent()) {
      credentials = cachedCredentials.get();
    } else {
      refreshCredentials();
    }
  }

  /**
   * Fetch new credentials from authorization server and store them in cache.
   *
   * @return true if the fetched credentials are different from the previously stored ones,
   *     otherwise returns false.
   */
  private boolean refreshCredentials() throws IOException {
    final ZeebeClientCredentials fetchedCredentials = fetchCredentials();
    credentialsCache.put(endpoint, fetchedCredentials).writeCache();

    if (fetchedCredentials.equals(credentials)) {
      return false;
    }

    credentials = fetchedCredentials;
    LOG.debug("Refreshed credentials.");
    return true;
  }

  private static String createJsonPayload(final OAuthCredentialsProviderBuilder builder) {
    try {
      final Map<String, String> payload = new HashMap<>();
      payload.put("client_id", builder.getClientId());
      payload.put("client_secret", builder.getClientSecret());
      payload.put("audience", builder.getAudience());
      payload.put("grant_type", "client_credentials");

      return JSON_MAPPER.writeValueAsString(payload);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ZeebeClientCredentials fetchCredentials() throws IOException {
    final HttpURLConnection connection =
        (HttpURLConnection) authorizationServerUrl.openConnection();
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Accept", "application/json");
    connection.setDoOutput(true);

    try (OutputStream os = connection.getOutputStream()) {
      final byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
      os.write(input, 0, input.length);
    }

    if (connection.getResponseCode() != 200) {
      throw new IOException(
          String.format(
              "Failed while requesting access token with status code %d and message %s.",
              connection.getResponseCode(), connection.getResponseMessage()));
    }

    try (InputStream in = connection.getInputStream();
        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {

      final ZeebeClientCredentials fetchedCredentials =
          CREDENTIALS_READER.readValue(CharStreams.toString(reader));

      if (fetchedCredentials == null) {
        throw new IOException("Expected valid credentials but got null instead.");
      }

      return fetchedCredentials;
    }
  }
}

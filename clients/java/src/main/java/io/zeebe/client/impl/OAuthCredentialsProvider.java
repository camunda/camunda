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
package io.zeebe.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.zeebe.client.CredentialsProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAuthCredentialsProvider implements CredentialsProvider {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ObjectReader JSON_READER = MAPPER.readerFor(ZeebeClientCredentials.class);
  private static final Logger LOG = LoggerFactory.getLogger(OAuthCredentialsProvider.class);
  private static final Key<String> HEADER_AUTH_KEY =
      Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final URL authorizationServerUrl;
  private final String jsonPayload;

  private ZeebeClientCredentials credentials;

  public OAuthCredentialsProvider(OAuthCredentialsProviderBuilder builder) {
    authorizationServerUrl = builder.getAuthorizationServer();
    jsonPayload = createJsonPayload(builder);
  }

  /** Adds an access token to the Authorization header of a gRPC call. */
  @Override
  public void applyCredentials(Metadata headers) {
    try {
      final ZeebeClientCredentials credentials = getCredentials();

      headers.put(
          HEADER_AUTH_KEY,
          String.format(
              "%s %s", credentials.getTokenType().trim(), credentials.getAccessToken().trim()));
    } catch (IOException e) {
      LOG.warn("Failed while fetching credentials, will not add credentials to rpc: ", e);
    }
  }

  private ZeebeClientCredentials getCredentials() throws IOException {
    if (credentials == null) {
      credentials = fetchCredentials();
    }

    return credentials;
  }

  private static String createJsonPayload(OAuthCredentialsProviderBuilder builder) {
    try {
      final Map<String, String> payload =
          new HashMap<String, String>() {
            {
              put("client_id", builder.getClientId());
              put("client_secret", builder.getClientSecret());
              put("audience", builder.getAudience());
              put("grant_type", "client_credentials");
            }
          };

      return MAPPER.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
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
        InputStreamReader reader = new InputStreamReader(in, Charsets.UTF_8)) {
      final String responseContent = CharStreams.toString(reader);
      return JSON_READER.readValue(responseContent);
    }
  }
}

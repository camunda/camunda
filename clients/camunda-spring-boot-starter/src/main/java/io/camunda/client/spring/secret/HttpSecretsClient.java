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
package io.camunda.client.spring.secret;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.InternalClientException;
import io.camunda.client.api.secret.SecretsClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SecretsClient} backed by the JDK HTTP client, pointing at {@code
 * <gateway>/v2/secrets/resolve}.
 */
public final class HttpSecretsClient implements SecretsClient {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<>() {};

  private final HttpClient http;
  private final URI endpoint;

  public HttpSecretsClient(final URI gatewayBaseUri) {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), gatewayBaseUri);
  }

  public HttpSecretsClient(final HttpClient http, final URI gatewayBaseUri) {
    this.http = http;
    final var base = gatewayBaseUri.toString();
    final var trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    endpoint = URI.create(trimmed + "/v2/secrets/resolve");
  }

  @Override
  public Map<String, String> resolve(final List<String> references) {
    if (references == null || references.isEmpty()) {
      return Map.of();
    }
    try {
      final var body = JSON.writeValueAsString(Map.of("references", references));
      final var request =
          HttpRequest.newBuilder(endpoint)
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(10))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      final var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() / 100 != 2) {
        throw new InternalClientException(
            "Secret resolution failed: HTTP " + response.statusCode());
      }
      final Map<String, Object> parsed = JSON.readValue(response.body(), RESPONSE_TYPE);
      final var resolvedValue = parsed.get("resolved");
      if (!(resolvedValue instanceof Map<?, ?> map)) {
        return Map.of();
      }
      final var result = new HashMap<String, String>();
      for (final var entry : map.entrySet()) {
        if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
          result.put(key, value);
        }
      }
      return result;
    } catch (final IOException e) {
      throw new InternalClientException("Failed to call secret resolution endpoint", e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InternalClientException("Interrupted while calling secret resolution endpoint", e);
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** {@link LogArchiveEventPlaneClient} on {@link HttpClient} (spec section 19.1). */
@NullMarked
public final class HttpLogArchiveEventPlaneClient implements LogArchiveEventPlaneClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final byte[] BODY_START = "{\"entries\":[".getBytes(StandardCharsets.UTF_8);
  private static final byte[] BODY_END = "]}".getBytes(StandardCharsets.UTF_8);

  private final HttpClient httpClient;
  private final String baseUrl;
  private final String clusterId;
  private final String physicalTenantId;
  private final @Nullable String authorization;
  private final Duration requestTimeout;

  public HttpLogArchiveEventPlaneClient(
      final String url,
      final String clusterId,
      final String physicalTenantId,
      final @Nullable String username,
      final @Nullable String password,
      final Duration requestTimeout) {
    baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    this.clusterId = clusterId;
    this.physicalTenantId = physicalTenantId;
    this.requestTimeout = requestTimeout;
    authorization =
        username == null || username.isEmpty()
            ? null
            : "Basic "
                + Base64.getEncoder()
                    .encodeToString(
                        (username + ":" + (password == null ? "" : password))
                            .getBytes(StandardCharsets.UTF_8));
    httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
  }

  @Override
  public CompletableFuture<Answer> send(
      final int partitionId, final String timelineId, final List<byte[]> entries) {
    final var builder =
        HttpRequest.newBuilder(uri(partitionId, timelineId))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body(entries)));
    if (authorization != null) {
      builder.header("Authorization", authorization);
    }
    return httpClient
        .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        .thenApply(response -> new Answer(response.statusCode(), nextSeq(response.body())));
  }

  URI uri(final int partitionId, final String timelineId) {
    return URI.create(
        baseUrl
            + "/v1/log-archive/"
            + encode(clusterId)
            + "/"
            + encode(physicalTenantId)
            + "/partitions/"
            + partitionId
            + "/timelines/"
            + encode(timelineId)
            + "/commits");
  }

  /** The raw entry bytes are embedded unchanged; the entries are deterministic JSON objects. */
  static byte[] body(final List<byte[]> entries) {
    final var out = new ByteArrayOutputStream();
    out.writeBytes(BODY_START);
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        out.write(',');
      }
      out.writeBytes(entries.get(i));
    }
    out.writeBytes(BODY_END);
    return out.toByteArray();
  }

  private static long nextSeq(final byte[] body) {
    try {
      final JsonNode node = MAPPER.readTree(body);
      final JsonNode nextSeq = node == null ? null : node.get("nextSeq");
      return nextSeq != null && nextSeq.canConvertToLong() ? nextSeq.asLong() : -1;
    } catch (final IOException e) {
      return -1;
    }
  }

  private static String encode(final String segment) {
    // URLEncoder encodes a space as '+', which is not valid in a path segment
    return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
  }
}

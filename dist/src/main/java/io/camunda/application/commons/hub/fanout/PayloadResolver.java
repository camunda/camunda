/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/** Returns an event's inline {@code data}, or fetches and verifies its claim-check. */
final class PayloadResolver {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient http;
  private final URI baseUrl;

  PayloadResolver(final HttpClient http, final URI baseUrl) {
    this.http = http;
    this.baseUrl = baseUrl;
  }

  JsonNode resolve(final CloudEventEnvelope event) throws IOException, InterruptedException {
    if (event.data() != null) {
      return event.data();
    }
    if (event.dataref() == null) {
      throw new IllegalStateException("Event " + event.seq() + " has neither data nor dataref");
    }
    final var response =
        http.send(
            HttpRequest.newBuilder(baseUrl.resolve(event.dataref()))
                .timeout(Duration.ofSeconds(30))
                .build(),
            BodyHandlers.ofByteArray());
    if (response.statusCode() != 200) {
      throw new IOException(
          "Fetching dataref " + event.dataref() + " failed with " + response.statusCode());
    }
    // Verify the raw bytes before parsing: size and digest describe exactly what Hub stored.
    final byte[] body = response.body();
    if (event.datasize() != null && body.length != event.datasize()) {
      throw new IOException(
          "dataref size mismatch: expected " + event.datasize() + ", got " + body.length);
    }
    if (event.datasha256() != null && !event.datasha256().equals(sha256Hex(body))) {
      throw new IOException("dataref sha256 mismatch for event " + event.seq());
    }
    return MAPPER.readTree(body);
  }

  static String sha256Hex(final byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

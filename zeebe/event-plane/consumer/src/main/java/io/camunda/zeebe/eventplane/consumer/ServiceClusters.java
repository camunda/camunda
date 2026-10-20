/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.zeebe.eventplane.store.StoreConfig;
import io.camunda.zeebe.eventplane.store.StoreConfig.Type;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Asks the service which store each cluster writes to: {@code GET /v1/clusters} lists the clusters,
 * and {@code GET /v1/credentials?cluster=…} gives the settings of one's store. A worker opens a
 * store for each and passes them to {@link EventPlaneConsumer.Builder#logStores}, owning them and
 * closing them after the worker. The settings hold secrets, which no message here quotes.
 */
public final class ServiceClusters {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private ServiceClusters() {}

  /**
   * The store settings of every cluster that the service knows, by cluster id; empty if it knows
   * none.
   *
   * @param service the service, such as {@code http://host:8085}
   * @throws ServiceResponseException if the service answers with another status than 200
   * @throws InvalidObjectJsonException if an answer isn't what the service answers
   * @throws IOException if the service can't be reached
   */
  public static Map<String, StoreConfig> fetch(final URI service)
      throws IOException, InterruptedException {
    final var text = service.toString();
    final var base = text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    try (final var client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(TIMEOUT)
            .build()) {
      final var clusters = URI.create(base + "/v1/clusters");
      final var stores = new LinkedHashMap<String, StoreConfig>();
      for (final var cluster : clusterIds(clusters, get(client, clusters))) {
        final var credentials =
            URI.create(
                base
                    + "/v1/credentials?cluster="
                    + URLEncoder.encode(cluster, StandardCharsets.UTF_8));
        stores.put(cluster, store(cluster, json(credentials, get(client, credentials))));
      }
      return Collections.unmodifiableMap(stores);
    }
  }

  private static byte[] get(final HttpClient client, final URI uri)
      throws IOException, InterruptedException {
    final var request =
        HttpRequest.newBuilder(uri)
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
    final var response = client.send(request, BodyHandlers.ofByteArray());
    if (response.statusCode() != 200) {
      throw new ServiceResponseException(
          uri, response.statusCode(), JsonFields.shown(response.body()));
    }
    return response.body();
  }

  private static JsonNode json(final URI uri, final byte[] body) throws InvalidObjectJsonException {
    try {
      return JsonFields.MAPPER.readTree(body);
    } catch (final IOException e) {
      // The location only: Jackson's message can quote what it read, which may be a secret.
      final var location =
          e instanceof final JsonProcessingException json ? json.getLocation() : null;
      throw new InvalidObjectJsonException(
          "the response of " + uri,
          "JSON",
          location == null
              ? "invalid JSON"
              : "invalid JSON at line %d, column %d"
                  .formatted(location.getLineNr(), location.getColumnNr()));
    }
  }

  private static List<String> clusterIds(final URI uri, final byte[] body)
      throws InvalidObjectJsonException {
    final var clusters = json(uri, body).get("clusters");
    if (clusters == null || !clusters.isObject()) {
      throw new InvalidObjectJsonException(
          "clusters", "a JSON object of cluster ids", describe(clusters));
    }
    final var ids = new ArrayList<String>();
    clusters.fieldNames().forEachRemaining(ids::add);
    return ids;
  }

  private static StoreConfig store(final String cluster, final JsonNode node)
      throws InvalidObjectJsonException {
    final var name = "credentials[" + cluster + "]";
    if (!node.isObject()) {
      throw new InvalidObjectJsonException(name, "a JSON object of store settings", describe(node));
    }
    final var type = text(name, node, "type");
    if (type == null) {
      throw new InvalidObjectJsonException(name + ".type", "filesystem or s3", "nothing");
    }
    final var path = text(name, node, "path");
    final var endpoint = text(name, node, "endpoint");
    try {
      return new StoreConfig(
          Type.of(type),
          path == null ? null : Path.of(path),
          text(name, node, "bucket"),
          text(name, node, "basePath"),
          text(name, node, "region"),
          endpoint == null ? null : uri(name + ".endpoint", endpoint),
          text(name, node, "accessKeyId"),
          text(name, node, "secretAccessKey"),
          forcePathStyle(name, node));
    } catch (final IllegalArgumentException e) {
      throw new InvalidObjectJsonException(
          name, "a valid store", "one that isn't (" + e.getMessage() + ")", e);
    }
  }

  private static @Nullable String text(final String name, final JsonNode store, final String field)
      throws InvalidObjectJsonException {
    final var value = store.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isTextual()) {
      throw new InvalidObjectJsonException(name + "." + field, "text", describe(value));
    }
    return value.textValue();
  }

  private static boolean forcePathStyle(final String name, final JsonNode store)
      throws InvalidObjectJsonException {
    final var value = store.get("forcePathStyle");
    if (value == null || value.isNull()) {
      return false;
    }
    if (!value.isBoolean()) {
      throw new InvalidObjectJsonException(
          name + ".forcePathStyle", "true or false", describe(value));
    }
    return value.booleanValue();
  }

  private static URI uri(final String field, final String value) throws InvalidObjectJsonException {
    try {
      return new URI(value);
    } catch (final URISyntaxException e) {
      throw new InvalidObjectJsonException(field, "a URI", "'" + value + "'", e);
    }
  }

  /** What a node is, not what it holds. */
  private static String describe(@Nullable final JsonNode node) {
    return node == null ? "nothing" : node.getNodeType().toString();
  }
}

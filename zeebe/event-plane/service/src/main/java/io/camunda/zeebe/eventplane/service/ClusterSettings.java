/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.zeebe.eventplane.store.InvalidStoreConfigException;
import io.camunda.zeebe.eventplane.store.StoreConfig;
import io.camunda.zeebe.eventplane.store.StoreConfig.Type;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Reads the registry of clusters from its setting: a JSON object of cluster id to the store that
 * cluster writes to, with the field names of {@link StoreConfig}. A wrong value is reported without
 * quoting the JSON, which holds secrets.
 */
final class ClusterSettings {

  private ClusterSettings() {}

  /**
   * The stores by cluster id.
   *
   * @param setting the setting's name, for the messages
   * @throws InvalidServiceSettingException if the JSON isn't an object of store configurations
   */
  static Map<String, StoreConfig> parse(final String setting, final String json) {
    final JsonNode root;
    try {
      root = Json.read(json.getBytes(StandardCharsets.UTF_8));
    } catch (final IOException e) {
      throw new InvalidServiceSettingException(setting, "to be JSON", invalidJson(e));
    }
    if (!root.isObject()) {
      throw new InvalidServiceSettingException(
          setting, "to be a JSON object of cluster ids and stores", root.getNodeType().toString());
    }
    final var clusters = new LinkedHashMap<String, StoreConfig>();
    for (final var cluster : root.properties()) {
      clusters.put(cluster.getKey(), store(setting + "." + cluster.getKey(), cluster.getValue()));
    }
    return clusters;
  }

  private static StoreConfig store(final String name, final JsonNode node) {
    if (!node.isObject()) {
      throw new InvalidServiceSettingException(
          name, "to be a JSON object of store settings", node.getNodeType().toString());
    }
    final var type = text(name, node, "type");
    if (type == null) {
      throw new InvalidServiceSettingException(name + ".type", "to be filesystem or s3", "none");
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
          endpoint == null ? null : ServiceSettings.uri(name + ".endpoint", endpoint),
          text(name, node, "accessKeyId"),
          text(name, node, "secretAccessKey"),
          forcePathStyle(name, node));
    } catch (final InvalidStoreConfigException e) {
      throw new InvalidServiceSettingException(
          name, "to be a valid store", "one that isn't (" + e.getMessage() + ")");
    }
  }

  private static @Nullable String text(
      final String name, final JsonNode store, final String field) {
    final var value = store.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isTextual()) {
      throw new InvalidServiceSettingException(
          name + "." + field, "to be text", value.getNodeType().toString());
    }
    return value.textValue();
  }

  private static boolean forcePathStyle(final String name, final JsonNode store) {
    final var value = store.get("forcePathStyle");
    if (value == null || value.isNull()) {
      return false;
    }
    if (!value.isBoolean()) {
      throw new InvalidServiceSettingException(
          name + ".forcePathStyle", "to be true or false", value.getNodeType().toString());
    }
    return value.booleanValue();
  }

  /** Where the JSON is invalid, without the text around it. */
  private static String invalidJson(final IOException e) {
    if (e instanceof final JsonProcessingException json && json.getLocation() != null) {
      final var location = json.getLocation();
      return "invalid JSON at line %d, column %d"
          .formatted(location.getLineNr(), location.getColumnNr());
    }
    return "invalid JSON";
  }
}

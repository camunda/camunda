/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import io.camunda.zeebe.eventplane.store.StoreConfig;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What {@code GET /v1/credentials} answers in the prototype: the store's own configuration, as a
 * worker would use it to open the store. There is no authorization, and the secret access key is in
 * it.
 */
final class StoreCredentials {

  /** What stands in for a secret that isn't shown. */
  static final String MASK = "***";

  private StoreCredentials() {}

  /** The JSON object of the store's settings, named like those of {@link StoreConfig}. */
  static String json(final StoreConfig config) {
    return new String(Json.write(settings(config, false)), StandardCharsets.UTF_8);
  }

  /**
   * The settings of the store, named like those of {@link StoreConfig}, those that are set.
   *
   * @param maskSecret whether to show {@link #MASK} instead of the secret access key
   */
  static Map<String, Object> settings(final StoreConfig config, final boolean maskSecret) {
    final var settings = new LinkedHashMap<String, Object>();
    settings.put("type", config.type().name().toLowerCase(Locale.ROOT));
    putIfSet(settings, "path", config.path());
    putIfSet(settings, "bucket", config.bucket());
    putIfSet(settings, "basePath", config.basePath());
    putIfSet(settings, "region", config.region());
    putIfSet(settings, "endpoint", config.endpoint());
    putIfSet(settings, "accessKeyId", config.accessKeyId());
    putIfSet(
        settings,
        "secretAccessKey",
        maskSecret ? mask(config.secretAccessKey()) : config.secretAccessKey());
    settings.put("forcePathStyle", config.forcePathStyle());
    return settings;
  }

  private static @Nullable String mask(@Nullable final String secret) {
    return secret == null ? null : MASK;
  }

  private static void putIfSet(
      final LinkedHashMap<String, Object> settings,
      final String name,
      @Nullable final Object value) {
    if (value != null) {
      settings.put(name, value.toString());
    }
  }
}

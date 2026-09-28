/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import io.camunda.zeebe.broker.exporter.metrics.MetricsExporter;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterInstantiationException;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector.ExporterInitializationInfo;
import io.camunda.zeebe.exporter.api.Exporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Copies of the broker's configured exporters for the scratch engine. Only exporters whose side
 * effects can be contained are copied: the metrics exporter as it is, and the Elasticsearch-backed
 * Camunda and Zeebe exporters redirected to a {@link ScratchSearchEngine}, with credentials, TLS,
 * plugins, schema management and notifications removed. Other exporters are left out.
 */
@NullMarked
final class ScratchExporters {

  static final String CAMUNDA_EXPORTER = "io.camunda.exporter.CamundaExporter";
  static final String ELASTICSEARCH_EXPORTER = "io.camunda.zeebe.exporter.ElasticsearchExporter";

  private static final ExporterInitializationInfo INITIALIZATION_INFO =
      new ExporterInitializationInfo(0, null);

  private ScratchExporters() {}

  static Map<ExporterDescriptor, ExporterInitializationInfo> of(
      final Collection<ExporterDescriptor> configured, final String searchUrl)
      throws ExporterInstantiationException {
    final var copies = new LinkedHashMap<ExporterDescriptor, ExporterInitializationInfo>();
    final var skipped = new ArrayList<String>();
    for (final var descriptor : configured) {
      final Class<? extends Exporter> exporterClass = descriptor.newInstance().getClass();
      final var args = copy(descriptor.getConfiguration().getArguments());
      final boolean contained =
          switch (exporterClass.getName()) {
            case CAMUNDA_EXPORTER -> redirectCamundaExporter(args, searchUrl);
            case ELASTICSEARCH_EXPORTER -> redirectElasticsearchExporter(args, searchUrl);
            default -> exporterClass == MetricsExporter.class;
          };
      if (contained) {
        copies.put(
            new ExporterDescriptor(descriptor.getId(), exporterClass, args), INITIALIZATION_INFO);
      } else {
        skipped.add(descriptor.getId());
      }
    }
    if (!skipped.isEmpty()) {
      LeaderWarmup.LOG.info("Leader warm-up does not run exporters {}", skipped);
    }
    return copies;
  }

  private static boolean redirectCamundaExporter(
      final Map<String, Object> args, final String searchUrl) {
    final var connect = child(args, "connect");
    final var type = get(connect, "type");
    if (type != null && !type.toString().equalsIgnoreCase("elasticsearch")) {
      return false;
    }
    put(connect, "url", searchUrl);
    put(connect, "urls", List.of());
    put(connect, "security", Map.of("enabled", false));
    put(connect, "awsEnabled", false);
    remove(connect, "isAwsEnabled");
    remove(connect, "username");
    remove(connect, "password");
    remove(connect, "interceptorPlugins");
    remove(connect, "proxy");
    put(args, "createSchema", false);
    remove(args, "notifier");
    return true;
  }

  private static boolean redirectElasticsearchExporter(
      final Map<String, Object> args, final String searchUrl) {
    put(args, "url", searchUrl);
    remove(args, "authentication");
    remove(args, "interceptorPlugins");
    remove(args, "proxy");
    put(child(args, "index"), "createTemplate", false);
    final var retention = child(args, "retention");
    put(retention, "enabled", false);
    put(retention, "managePolicy", false);
    return true;
  }

  /**
   * Exporter arguments may spell a key in camel, kebab or lower case, and exporters read them
   * without regard to case or separators, so keys are matched the same way here.
   */
  private static String normalise(final String key) {
    return key.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
  }

  private static @Nullable Object get(final Map<String, Object> map, final String key) {
    for (final var entry : map.entrySet()) {
      if (normalise(entry.getKey()).equals(normalise(key))) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static void remove(final Map<String, Object> map, final String key) {
    map.keySet().removeIf(existing -> normalise(existing).equals(normalise(key)));
  }

  private static void put(final Map<String, Object> map, final String key, final Object value) {
    remove(map, key);
    map.put(key, value);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> child(final Map<String, Object> map, final String key) {
    final var existing = get(map, key);
    final Map<String, Object> child =
        existing instanceof Map<?, ?> nested
            ? (Map<String, Object>) nested
            : new LinkedHashMap<String, Object>();
    put(map, key, child);
    return child;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> copy(final @Nullable Map<String, Object> args) {
    final var copy = new LinkedHashMap<String, Object>();
    if (args != null) {
      args.forEach((key, value) -> copy.put(key, copyValue(value)));
    }
    return copy;
  }

  @SuppressWarnings("unchecked")
  private static @Nullable Object copyValue(final @Nullable Object value) {
    if (value instanceof Map<?, ?> map) {
      return copy((Map<String, Object>) map);
    }
    if (value instanceof List<?> list) {
      final var copy = new ArrayList<@Nullable Object>(list.size());
      list.forEach(item -> copy.add(copyValue(item)));
      return copy;
    }
    return value;
  }
}

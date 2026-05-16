/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.config;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConfigParser {

  private static final int DEFAULT_MAX_BATCH_SIZE = 100;
  private static final int DEFAULT_MAX_QUEUE_SIZE = 10_000;
  private static final OverflowPolicy DEFAULT_OVERFLOW_POLICY = OverflowPolicy.BLOCK;
  private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(1);

  private static final long DEFAULT_REQUEST_TIMEOUT_MS = 5_000;
  private static final long DEFAULT_MAX_BLOCKING_TIMEOUT_MS = 1_000;
  private static final long DEFAULT_CLOSE_TIMEOUT_MS = 5_000;
  private static final String DEFAULT_CLIENT_ID = "zeebe";

  private static final RecordConfiguration DEFAULT_RECORD_CONFIG =
      new RecordConfiguration("zeebe", Set.of(RecordType.EVENT));

  private ConfigParser() {}

  public static Config parse(final RawConfig rawConfig) {
    final RawConfig safeRawConfig = rawConfig == null ? new RawConfig() : rawConfig;

    final int maxBatchSize =
        safeRawConfig.maxBatchSize == null ? DEFAULT_MAX_BATCH_SIZE : safeRawConfig.maxBatchSize;
    if (maxBatchSize <= 0) {
      throw new IllegalArgumentException("maxBatchSize must be greater than 0");
    }

    final int maxQueueSize =
        safeRawConfig.maxQueueSize == null ? DEFAULT_MAX_QUEUE_SIZE : safeRawConfig.maxQueueSize;
    if (maxQueueSize <= 0) {
      throw new IllegalArgumentException("maxQueueSize must be greater than 0");
    }

    final OverflowPolicy overflowPolicy =
        safeRawConfig.overflowPolicy == null
            ? DEFAULT_OVERFLOW_POLICY
            : OverflowPolicy.valueOf(safeRawConfig.overflowPolicy.trim().toUpperCase());

    final Duration flushInterval =
        Duration.ofMillis(
            safeRawConfig.flushIntervalMs == null
                ? DEFAULT_FLUSH_INTERVAL.toMillis()
                : safeRawConfig.flushIntervalMs);

    final ProducerConfiguration producer = parseProducerConfig(safeRawConfig.producer);
    final RecordsConfiguration records = parseRecordsConfig(safeRawConfig.records);

    return new Config(producer, records, maxBatchSize, maxQueueSize, overflowPolicy, flushInterval);
  }

  private static ProducerConfiguration parseProducerConfig(
      final RawProducerConfig rawProducerConfig) {
    final RawProducerConfig safeRawProducer =
        rawProducerConfig == null ? new RawProducerConfig() : rawProducerConfig;

    final String servers = trimToNull(safeRawProducer.servers);
    if (servers == null) {
      throw new IllegalArgumentException("producer.servers must be configured");
    }

    final String clientId =
        safeRawProducer.clientId == null ? DEFAULT_CLIENT_ID : safeRawProducer.clientId;
    final long requestTimeoutMs =
        safeRawProducer.requestTimeoutMs == null
            ? DEFAULT_REQUEST_TIMEOUT_MS
            : safeRawProducer.requestTimeoutMs;
    final long maxBlockingTimeoutMs =
        safeRawProducer.maxBlockingTimeoutMs == null
            ? DEFAULT_MAX_BLOCKING_TIMEOUT_MS
            : safeRawProducer.maxBlockingTimeoutMs;
    final long closeTimeoutMs =
        safeRawProducer.closeTimeoutMs == null
            ? DEFAULT_CLOSE_TIMEOUT_MS
            : safeRawProducer.closeTimeoutMs;

    return new ProducerConfiguration(
        servers,
        clientId,
        requestTimeoutMs,
        maxBlockingTimeoutMs,
        closeTimeoutMs,
        parseAdditionalConfig(safeRawProducer.config));
  }

  private static RecordsConfiguration parseRecordsConfig(final RawRecordsConfig rawRecordsConfig) {
    final RawRecordsConfig safeRawRecords =
        rawRecordsConfig == null ? new RawRecordsConfig() : rawRecordsConfig;

    final RecordConfiguration defaults =
        mergeRecordConfig(DEFAULT_RECORD_CONFIG, safeRawRecords.defaults);

    // Use explicit EnumMap — no reflection, no camelCase→UPPER_SNAKE_CASE conversion,
    // and works correctly under GraalVM native image.
    final Map<ValueType, RecordConfiguration> byType = new EnumMap<>(ValueType.class);
    for (final Map.Entry<ValueType, RawRecordConfig> entry :
        safeRawRecords.byValueType().entrySet()) {
      byType.put(entry.getKey(), mergeRecordConfig(defaults, entry.getValue()));
    }

    return new RecordsConfiguration(defaults, Collections.unmodifiableMap(byType));
  }

  private static RecordConfiguration mergeRecordConfig(
      final RecordConfiguration base, final RawRecordConfig override) {
    if (override == null) {
      return base;
    }

    final String topic = trimToNull(override.topic) == null ? base.topic() : override.topic;
    final Set<RecordType> allowedTypes =
        trimToNull(override.type) == null ? base.allowedTypes() : parseAllowedTypes(override.type);

    return new RecordConfiguration(topic, allowedTypes);
  }

  private static Set<RecordType> parseAllowedTypes(final String rawTypes) {
    return Arrays.stream(rawTypes.split(","))
        .map(String::trim)
        .filter(type -> !type.isBlank())
        .map(String::toUpperCase)
        .map(type -> type.equals("REJECTION") ? "COMMAND_REJECTION" : type)
        .map(RecordType::valueOf)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Map<String, String> parseAdditionalConfig(final String config) {
    if (trimToNull(config) == null) {
      return Map.of();
    }

    final Properties properties = new Properties();
    try {
      properties.load(new StringReader(config));
    } catch (final IOException e) {
      throw new IllegalArgumentException("Unable to parse producer.config", e);
    }

    return properties.entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
  }

  private static String trimToNull(final String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}

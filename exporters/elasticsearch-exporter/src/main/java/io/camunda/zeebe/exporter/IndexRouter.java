/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Computes the name of the index, alias, or search pattern for a record or its value type. */
final class IndexRouter {
  private static final DateTimeFormatter DEFAULT_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
  private static final String INDEX_DELIMITER = "_";
  private static final String ALIAS_DELIMITER = "-";

  private final DateTimeFormatter formatter;
  private final IndexConfiguration config;

  IndexRouter(final IndexConfiguration config) {
    this(config, DEFAULT_FORMATTER);
  }

  IndexRouter(final IndexConfiguration config, final DateTimeFormatter formatter) {
    this.config = config;
    this.formatter = formatter;
  }

  /** Returns the name of the index for the given record. */
  String indexFor(final Record<?> record) {
    final Instant timestamp = Instant.ofEpochMilli(record.getTimestamp());
    return (indexPrefixForValueType(record.getValueType()) + INDEX_DELIMITER)
        + formatter.format(timestamp);
  }

  String idFor(final Record<?> record) {
    return record.getPartitionId() + "-" + record.getPosition();
  }

  String aliasNameForValueType(final ValueType valueType) {
    return config.prefix + ALIAS_DELIMITER + valueTypeToString(valueType);
  }

  String indexPrefixForValueType(final ValueType valueType) {
    final String version = VersionUtil.getVersionLowerCase();
    return config.prefix
        + INDEX_DELIMITER
        + valueTypeToString(valueType)
        + INDEX_DELIMITER
        + version;
  }

  String searchPatternForValueType(final ValueType valueType) {
    return indexPrefixForValueType(valueType) + INDEX_DELIMITER + "*";
  }

  private String valueTypeToString(final ValueType valueType) {
    return valueType.name().toLowerCase().replace("_", "-");
  }
}

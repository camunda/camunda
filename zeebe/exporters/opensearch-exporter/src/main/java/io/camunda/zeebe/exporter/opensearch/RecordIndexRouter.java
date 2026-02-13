/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Computes the name of the index, alias, or search pattern for a record or its value type. */
final class RecordIndexRouter {
  public static final String INDEX_DELIMITER = "_";
  private static final DateTimeFormatter DEFAULT_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
  private static final String ALIAS_DELIMITER = "-";

  private final DateTimeFormatter formatter;
  private final IndexConfiguration config;

  RecordIndexRouter(final IndexConfiguration config) {
    this(config, DEFAULT_FORMATTER);
  }

  RecordIndexRouter(final IndexConfiguration config, final DateTimeFormatter formatter) {
    this.config = config;
    this.formatter = formatter;
  }

  /**
   * Returns the name of the index for the given record. This consists of the configured prefix,
   * followed by the value type, the current broker version, and then the current date.
   */
  String indexFor(final Record<?> record) {
    final Instant timestamp = Instant.ofEpochMilli(record.getTimestamp());
    return (indexPrefixForValueType(record.getValueType(), record.getBrokerVersion())
            + INDEX_DELIMITER)
        + formatter.format(timestamp);
  }

  /** Returns a cluster-unique ID for the record consisting of it's "partitionId-position". */
  String idFor(final Record<?> record) {
    return record.getPartitionId() + "-" + record.getPosition();
  }

  /**
   * Returns the index template's alias name for the given value type, prefixed by {@link
   * IndexConfiguration#prefix}, e.g. for {@link ValueType#VARIABLE}, you get
   * "my-super-prefix-variable".
   */
  String aliasNameForValueType(final ValueType valueType) {
    return config.prefix + ALIAS_DELIMITER + valueTypeToString(valueType);
  }

  /** Returns the index for this value type, minus the current date. */
  String indexPrefixForValueType(final ValueType valueType, final String version) {
    return config.prefix
        + INDEX_DELIMITER
        + valueTypeToString(valueType)
        + INDEX_DELIMITER
        + version;
  }

  /**
   * Returns the search pattern for this value type, which consists of the index followed by a
   * separator and a wildcard, without the date. This allows one to search for this pattern and get
   * all indices regardless of their date.
   */
  String searchPatternForValueType(final ValueType valueType, final String version) {
    return indexPrefixForValueType(valueType, version) + INDEX_DELIMITER + "*";
  }

  /**
   * Returns the routing for this record. The routing field of a document controls to which shard it
   * will be assigned.
   */
  String routingFor(final Record<?> record) {
    return String.valueOf(record.getPartitionId());
  }

  private String valueTypeToString(final ValueType valueType) {
    return valueType.name().toLowerCase().replace("_", "-");
  }
}

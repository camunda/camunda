/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.SemanticVersion;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Computes the name of the index, alias, or search pattern for a record or its value type. */
final class RecordIndexRouter {
  static final String INDEX_DELIMITER = "_";
  private static final String ALIAS_DELIMITER = "-";

  /**
   * For each release line, the first version whose records let Elasticsearch route documents by
   * their id; records of older versions keep the legacy partition id routing, see {@link
   * #routingFor(Record)}. Ordered by ascending version.
   *
   * <p>There is one entry per line because the change is backported, and every branch that carries
   * it must carry the same table: a broker re-exports records written by other versions, and the
   * two have to agree on how such a record was routed. Adding a line that does not ship the change,
   * or omitting one that does, therefore reintroduces the duplicate documents described in {@link
   * #routingFor(Record)}.
   */
  private static final List<SemanticVersion> ROUTE_BY_ID_SINCE =
      List.of(
          new SemanticVersion(8, 8, 37, null, null),
          new SemanticVersion(8, 9, 18, null, null),
          new SemanticVersion(8, 10, 0, null, null),
          new SemanticVersion(8, 11, 0, null, null));

  private final DateTimeFormatter formatter;
  private final IndexConfiguration config;

  RecordIndexRouter(final IndexConfiguration config) {
    this(
        config,
        DateTimeFormatter.ofPattern(config.indexSuffixDatePattern).withZone(ZoneOffset.UTC));
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
   * Returns the routing for this record, or {@code null} to let Elasticsearch route the document by
   * its id. The routing of a document controls to which shard it will be assigned.
   *
   * <p>Routing by partition id yields only as many distinct routing values as there are partitions
   * (typically a small number, e.g. 1, 2, 3), which Elasticsearch hashes into a handful of shards
   * with collisions. Some shards then stay empty while others hold several partitions' worth of
   * data, creating hotspots. The document id is unique per record (see {@link #idFor(Record)}), so
   * routing by it spreads records evenly across all shards; returning {@code null} achieves that
   * without storing a redundant routing value, as Elasticsearch falls back to hashing the id.
   *
   * <p>Records written by versions older than {@link #ROUTE_BY_ID_SINCE} keep the legacy partition
   * id routing. Both the index name (see {@link #indexFor(Record)}) and this decision derive from
   * the record's broker version, so an index only ever holds one of the two schemes. Were both to
   * mix within an index, re-exporting a record after an upgrade would write it to a different shard
   * than the copy already there, duplicating the document instead of overwriting it.
   */
  String routingFor(final Record<?> record) {
    return isRoutedById(record) ? null : String.valueOf(record.getPartitionId());
  }

  private boolean isRoutedById(final Record<?> record) {
    final var version = SemanticVersion.parse(record.getBrokerVersion());
    if (version.isEmpty()) {
      // Without a version to compare, assume the legacy routing: routing a record by id into an
      // index that may already hold it under the legacy scheme would duplicate the document.
      return false;
    }

    final var recordVersion = withoutPreRelease(version.get());
    return ROUTE_BY_ID_SINCE.stream()
        .filter(since -> isSameLine(since, recordVersion))
        .findFirst()
        .map(since -> recordVersion.compareTo(since) >= 0)
        // A line that has no entry predates the change, unless the record comes from a line newer
        // than any known one. That happens while a cluster is being upgraded, when a broker of the
        // older version re-exports records another broker has already written.
        .orElseGet(() -> recordVersion.compareTo(ROUTE_BY_ID_SINCE.getLast()) > 0);
  }

  private static boolean isSameLine(final SemanticVersion left, final SemanticVersion right) {
    return left.major() == right.major() && left.minor() == right.minor();
  }

  /**
   * Pre-releases have a lower precedence than the version they lead up to, which would leave alpha
   * and snapshot builds of a version in {@link #ROUTE_BY_ID_SINCE} on the legacy routing.
   */
  private static SemanticVersion withoutPreRelease(final SemanticVersion version) {
    return new SemanticVersion(version.major(), version.minor(), version.patch(), null, null);
  }

  private String valueTypeToString(final ValueType valueType) {
    return valueType.name().toLowerCase().replace("_", "-");
  }
}

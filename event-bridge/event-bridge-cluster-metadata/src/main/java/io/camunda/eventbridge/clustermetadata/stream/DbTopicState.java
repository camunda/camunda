/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RocksDB-backed topic registry (desired state), keyed by {@code topicName} → encoded {@link
 * TopicMetadata}. Rebuilt identically on every replica via stream replay, so a new coordinator
 * leader restores the topic set after failover.
 */
public final class DbTopicState {

  private final DbString topicName = new DbString();
  private final DbString payload = new DbString();
  private final ColumnFamily<DbString, DbString> topicColumnFamily;

  public DbTopicState(
      final ZeebeDb<MetadataColumnFamilies> zeebeDb, final TransactionContext context) {
    topicColumnFamily =
        zeebeDb.createColumnFamily(
            MetadataColumnFamilies.TOPIC_REGISTRY, context, topicName, payload);
  }

  public void put(final String name, final TopicMetadata metadata) {
    topicName.wrapString(name);
    payload.wrapString(metadata.encode());
    topicColumnFamily.upsert(topicName, payload);
  }

  public void delete(final String name) {
    topicName.wrapString(name);
    topicColumnFamily.deleteIfExists(topicName);
  }

  public TopicMetadata get(final String name) {
    topicName.wrapString(name);
    final var value = topicColumnFamily.get(topicName);
    return value == null ? null : TopicMetadata.decode(value.toString());
  }

  /**
   * Returns all registered topics ({@code topicName → metadata}) for failover rebuild / listing.
   */
  public Map<String, TopicMetadata> readAll() {
    final var result = new LinkedHashMap<String, TopicMetadata>();
    topicColumnFamily.forEach(
        (key, value) -> result.put(key.toString(), TopicMetadata.decode(value.toString())));
    return result;
  }
}

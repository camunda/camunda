/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.topic;

import io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies;
import io.camunda.eventbridge.clustermetadata.state.mutable.MutableTopicState;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RocksDB-backed {@link MutableTopicState}: the topic registry (desired state), keyed by {@code
 * topicName} → {@link PersistedTopic} (structured msgpack — no hand-rolled string encoding).
 * Rebuilt identically on every replica via stream replay, so a new metadata leader restores the
 * topic set after failover.
 *
 * <p>The appliers are the only writers; reads come straight from the column family — the durable
 * source of truth — so there is no in-memory mirror to keep in lockstep. The flyweight keys/values
 * belong to one {@link io.camunda.zeebe.db.TransactionContext}, so an instance is single-actor: the
 * stream processor holds one, and each off-actor reader gets its own through a {@link
 * TopicQueryService}. {@link #topicsSnapshot} pins its result (copies out of the flyweights) so a
 * caller may keep it after the next read mutates them.
 */
public final class DbTopicState implements MutableTopicState {

  private final DbString topicName = new DbString();
  private final PersistedTopic persistedTopic = new PersistedTopic();
  private final ColumnFamily<DbString, PersistedTopic> topicColumnFamily;

  public DbTopicState(
      final ZeebeDb<MetadataColumnFamilies> zeebeDb, final TransactionContext context) {
    topicColumnFamily =
        zeebeDb.createColumnFamily(
            MetadataColumnFamilies.TOPIC_REGISTRY, context, topicName, persistedTopic);
  }

  @Override
  public TopicMetadata get(final String name) {
    topicName.wrapString(name);
    final var stored = topicColumnFamily.get(topicName);
    return stored == null ? null : stored.toMetadata();
  }

  @Override
  public Map<String, TopicMetadata> topicsSnapshot() {
    // Pin the result: copy the name out of the key flyweight and decode each entry into a fresh
    // immutable TopicMetadata, so the snapshot survives the next read that rewraps the flyweights.
    final var snapshot = new LinkedHashMap<String, TopicMetadata>();
    topicColumnFamily.forEach((key, value) -> snapshot.put(key.toString(), value.toMetadata()));
    return snapshot;
  }

  @Override
  public void put(final String name, final TopicMetadata metadata) {
    topicName.wrapString(name);
    topicColumnFamily.upsert(topicName, persistedTopic.wrap(metadata));
  }

  @Override
  public void delete(final String name) {
    topicName.wrapString(name);
    topicColumnFamily.deleteIfExists(topicName);
  }
}

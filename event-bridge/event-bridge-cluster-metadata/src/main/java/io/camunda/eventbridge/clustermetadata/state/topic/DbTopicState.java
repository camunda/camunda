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
import java.util.Set;

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

  private final PersistedTopicLeaders persistedLeaders = new PersistedTopicLeaders();
  private final ColumnFamily<DbString, PersistedTopicLeaders> leaderColumnFamily;

  public DbTopicState(
      final ZeebeDb<MetadataColumnFamilies> zeebeDb, final TransactionContext context) {
    topicColumnFamily =
        zeebeDb.createColumnFamily(
            MetadataColumnFamilies.TOPIC_REGISTRY, context, topicName, persistedTopic);
    leaderColumnFamily =
        zeebeDb.createColumnFamily(
            MetadataColumnFamilies.TOPIC_PARTITION_LEADER, context, topicName, persistedLeaders);
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
    leaderColumnFamily.deleteIfExists(topicName);
  }

  @Override
  public Set<Integer> partitionsWithLeader(final String name) {
    topicName.wrapString(name);
    final var stored = leaderColumnFamily.get(topicName);
    return stored == null ? Set.of() : Set.copyOf(stored.toMap().keySet());
  }

  @Override
  public long leaderTerm(final String name, final int partition) {
    topicName.wrapString(name);
    final var stored = leaderColumnFamily.get(topicName);
    if (stored == null) {
      return -1L;
    }
    final var leader = stored.toMap().get(partition);
    return leader == null ? -1L : leader[1];
  }

  @Override
  public int leaderNode(final String name, final int partition) {
    topicName.wrapString(name);
    final var stored = leaderColumnFamily.get(topicName);
    if (stored == null) {
      return -1;
    }
    final var leader = stored.toMap().get(partition);
    return leader == null ? -1 : (int) leader[0];
  }

  @Override
  public void recordPartitionLeader(
      final String name, final int partition, final int node, final long term) {
    topicName.wrapString(name);
    final var stored = leaderColumnFamily.get(topicName);
    final var leaders =
        stored == null ? new LinkedHashMap<Integer, long[]>() : new LinkedHashMap<>(stored.toMap());
    leaders.put(partition, new long[] {node, term});
    topicName.wrapString(name);
    leaderColumnFamily.upsert(topicName, persistedLeaders.set(leaders));
  }
}

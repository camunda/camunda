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
import java.util.concurrent.ConcurrentHashMap;

/**
 * RocksDB-backed {@link MutableTopicState}: the topic registry (desired state), keyed by {@code
 * topicName} → encoded {@link TopicMetadata}. Rebuilt identically on every replica via stream
 * replay, so a new metadata leader restores the topic set after failover.
 *
 * <p>The appliers are the only writers; granular {@link #put}/{@link #delete} keep a thread-safe
 * {@link TopicMetadata} mirror in lockstep with the durable state. RocksDB reads ({@link #get}) run
 * on the stream-processing actor only; the mirror read ({@link #topicsSnapshot}) is safe off-actor
 * (the metadata leader and each broker's reconcile).
 */
public final class DbTopicState implements MutableTopicState {

  private final DbString topicName = new DbString();
  private final DbString payload = new DbString();
  private final ColumnFamily<DbString, DbString> topicColumnFamily;

  // Thread-safe mirror of the registry, maintained by the appliers (on the stream actor) and read
  // off-actor; the column family is the durable source of truth, this is seeded from it on start.
  private final Map<String, TopicMetadata> mirror = new ConcurrentHashMap<>();

  public DbTopicState(
      final ZeebeDb<MetadataColumnFamilies> zeebeDb, final TransactionContext context) {
    topicColumnFamily =
        zeebeDb.createColumnFamily(
            MetadataColumnFamilies.TOPIC_REGISTRY, context, topicName, payload);
  }

  @Override
  public TopicMetadata get(final String name) {
    topicName.wrapString(name);
    final var value = topicColumnFamily.get(topicName);
    return value == null ? null : TopicMetadata.decode(value.toString());
  }

  @Override
  public Map<String, TopicMetadata> topicsSnapshot() {
    return new LinkedHashMap<>(mirror);
  }

  @Override
  public void put(final String name, final TopicMetadata metadata) {
    topicName.wrapString(name);
    payload.wrapString(metadata.encode());
    topicColumnFamily.upsert(topicName, payload);
    mirror.put(name, metadata);
  }

  @Override
  public void delete(final String name) {
    topicName.wrapString(name);
    topicColumnFamily.deleteIfExists(topicName);
    mirror.remove(name);
  }

  @Override
  public void seedMirror() {
    mirror.clear();
    topicColumnFamily.forEach(
        (key, value) -> mirror.put(key.toString(), TopicMetadata.decode(value.toString())));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.offset;

import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * A read-only view of the committed offsets, read directly from RocksDB on its <em>own</em> {@link
 * ZeebeDb} context — the event-bridge counterpart of Zeebe's {@code StateQueryService}. Offsets are
 * unbounded (every group × every owned partition), so they are <b>not</b> kept in an in-memory
 * mirror; the coordinator reads them on demand instead, off the stream-processing actor (a separate
 * context sees committed state and shares no flyweight buffers with the processor's column family).
 *
 * <p>This must be used from a single actor (the {@link
 * io.camunda.eventbridge.consumergroups.membership.ConsumerGroupCoordinator}); its column family is
 * opened lazily on first use so the handles are created on that reader thread.
 */
public final class OffsetQueryService {

  private final ZeebeDb<EventBridgeColumnFamilies> zeebeDb;

  private DbString groupId;
  private DbString topic;
  private DbInt partitionId;
  private DbCompositeKey<DbString, DbCompositeKey<DbString, DbInt>> offsetKey;
  private ColumnFamily<DbCompositeKey<DbString, DbCompositeKey<DbString, DbInt>>, DbLong>
      offsetsColumnFamily;

  public OffsetQueryService(final ZeebeDb<EventBridgeColumnFamilies> zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  /** A group's committed offsets ({@code (topic, partition) → position}), read from state. */
  public Map<TopicPartition, Long> committedOffsets(final String group) {
    ensureOpened();
    groupId.wrapString(group);
    final var result = new TreeMap<TopicPartition, Long>();
    final BiConsumer<DbCompositeKey<DbString, DbCompositeKey<DbString, DbInt>>, DbLong> visitor =
        (key, value) -> {
          final var topicPartition = key.second();
          result.put(
              new TopicPartition(
                  topicPartition.first().toString(), topicPartition.second().getValue()),
              value.getValue());
        };
    offsetsColumnFamily.whileEqualPrefix(groupId, visitor);
    return result;
  }

  private void ensureOpened() {
    if (offsetsColumnFamily != null) {
      return;
    }
    groupId = new DbString();
    topic = new DbString();
    partitionId = new DbInt();
    final var topicPartitionKey = new DbCompositeKey<>(topic, partitionId);
    offsetKey = new DbCompositeKey<>(groupId, topicPartitionKey);
    offsetsColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_OFFSETS,
            zeebeDb.createContext(),
            offsetKey,
            new DbLong());
  }
}

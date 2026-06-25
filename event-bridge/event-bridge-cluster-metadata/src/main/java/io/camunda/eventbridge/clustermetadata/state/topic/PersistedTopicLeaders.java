/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.topic;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A topic's per-partition leadership, stored in the {@link
 * io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies#TOPIC_PARTITION_LEADER}
 * column family keyed by topic name: {@code partition → {leader node id, Raft term}}. Each
 * partition's elected Raft leader reports itself here, so this is the authoritative, replicated
 * record of which partitions have a leader — the basis for deriving topic readiness, and routing
 * authority.
 */
public final class PersistedTopicLeaders extends UnpackedObject implements DbValue {

  private final ArrayProperty<Entry> leadersProp = new ArrayProperty<>("leaders", Entry::new);

  public PersistedTopicLeaders() {
    super(1);
    declareProperty(leadersProp);
  }

  /** Leadership as {@code partition → [leader node id, term]}. */
  public Map<Integer, long[]> toMap() {
    final Map<Integer, long[]> leaders = new LinkedHashMap<>();
    leadersProp.forEach(
        entry -> leaders.put(entry.getPartition(), new long[] {entry.getNode(), entry.getTerm()}));
    return leaders;
  }

  /** Replaces all entries with {@code partition → [leader node id, term]}. */
  public PersistedTopicLeaders set(final Map<Integer, long[]> leaders) {
    leadersProp.reset();
    leaders.forEach(
        (partition, leader) ->
            leadersProp.add().setPartition(partition).setNode((int) leader[0]).setTerm(leader[1]));
    return this;
  }

  /** One partition's leader: node id + the Raft term it was reported in. */
  public static final class Entry extends ObjectValue {
    private final IntegerProperty partitionProp = new IntegerProperty("partition", 0);
    private final IntegerProperty nodeProp = new IntegerProperty("node", -1);
    private final LongProperty termProp = new LongProperty("term", -1L);

    public Entry() {
      super(3);
      declareProperty(partitionProp).declareProperty(nodeProp).declareProperty(termProp);
    }

    public int getPartition() {
      return partitionProp.getValue();
    }

    public Entry setPartition(final int partition) {
      partitionProp.setValue(partition);
      return this;
    }

    public int getNode() {
      return nodeProp.getValue();
    }

    public Entry setNode(final int node) {
      nodeProp.setValue(node);
      return this;
    }

    public long getTerm() {
      return termProp.getValue();
    }

    public Entry setTerm(final long term) {
      termProp.setValue(term);
      return this;
    }
  }
}

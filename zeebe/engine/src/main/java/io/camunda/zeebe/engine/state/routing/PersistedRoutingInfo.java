/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.routing;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.immutable.RoutingState.MessageCorrelation;
import io.camunda.zeebe.engine.state.immutable.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import org.agrona.collections.IntArrayList;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class PersistedRoutingInfo extends MsgpackBufferValue<PersistedRoutingInfo>
    implements DbValue {
  final IntArrayList partitions = new IntArrayList();
  MessageCorrelationStrategy messageCorrelationStrategy = MessageCorrelationStrategy.HASH_MOD;
  int hashModPartitionCount = -1;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public SortedSet<Integer> getPartitions() {
    return Collections.unmodifiableSortedSet(new TreeSet<>(partitions));
  }

  public void setPartitions(final SortedSet<Integer> partitions) {
    this.partitions.clear();
    for (final var partition : partitions) {
      this.partitions.addInt(partition);
    }
  }

  public MessageCorrelation getMessageCorrelation() {
    return switch (messageCorrelationStrategy) {
      case HASH_MOD -> new MessageCorrelation.HashMod(hashModPartitionCount);
    };
  }

  public void setMessageCorrelation(final MessageCorrelation messageCorrelation) {
    switch (messageCorrelation) {
      case HashMod(final int partitionCount) -> {
        messageCorrelationStrategy = MessageCorrelationStrategy.HASH_MOD;
        hashModPartitionCount = partitionCount;
      }
    }
  }

  @Override
  protected MsgpackCodec<PersistedRoutingInfo> codec() {
    return PersistedRoutingInfoCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedRoutingInfo> ops() {
    return PersistedRoutingInfoOps.INSTANCE;
  }

  enum MessageCorrelationStrategy {
    HASH_MOD,
  }
}

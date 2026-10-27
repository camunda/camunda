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
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class PersistedRoutingInfo extends UnpackedObject implements DbValue {
  private final ArrayProperty<IntegerValue> partitions =
      new ArrayProperty<>("partitions", IntegerValue::new);
  private final EnumProperty<MessageCorrelationStrategy> messageCorrelationStrategy =
      new EnumProperty<>(
          "messageCorrelationStrategy",
          MessageCorrelationStrategy.class,
          MessageCorrelationStrategy.HASH_MOD);
  private final IntegerProperty hashModPartitionCount =
      new IntegerProperty("hashModPartitionCount", -1);

  public PersistedRoutingInfo() {
    super(3);
    declareProperty(partitions)
        .declareProperty(messageCorrelationStrategy)
        .declareProperty(hashModPartitionCount);
  }

  public SortedSet<Integer> getPartitions() {
    return partitions.stream()
        .map(IntegerValue::getValue)
        .collect(
            Collectors.collectingAndThen(
                Collectors.toCollection(TreeSet::new), Collections::unmodifiableSortedSet));
  }

  public void setPartitions(final SortedSet<Integer> partitions) {
    this.partitions.reset();
    for (final var partition : partitions) {
      this.partitions.add().setValue(partition);
    }
  }

  public MessageCorrelation getMessageCorrelation() {
    return switch (messageCorrelationStrategy.getValue()) {
      case HASH_MOD -> new MessageCorrelation.HashMod(hashModPartitionCount.getValue());
    };
  }

  public void setMessageCorrelation(final MessageCorrelation messageCorrelation) {
    switch (messageCorrelation) {
      case HashMod(final int partitionCount) -> {
        messageCorrelationStrategy.setValue(MessageCorrelationStrategy.HASH_MOD);
        hashModPartitionCount.setValue(partitionCount);
      }
    }
  }

  enum MessageCorrelationStrategy {
    HASH_MOD,
  }
}

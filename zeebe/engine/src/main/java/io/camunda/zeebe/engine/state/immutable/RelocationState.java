/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil.Routing;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import java.util.Collection;
import java.util.Set;
import org.agrona.DirectBuffer;

public interface RelocationState extends Routing {

  @Override
  default int partitionForCorrelationKey(final DirectBuffer correlationKey) {
    return getRoutingInfo().partitionForCorrelationKey(correlationKey);
  }

  RoutingInfo getRoutingInfo();

  boolean isRelocating(final DirectBuffer correlationKey);

  boolean isRelocated(final DirectBuffer correlationKey);

  Collection<MessageRecord> getQueuedMessages();

  record RoutingInfo(
      int currentPartitionCount, int newPartitionCount, Set<Integer> completedPartitions)
      implements Routing {

    public int oldPartitionForCorrelationKey(final DirectBuffer correlationKey) {
      return SubscriptionUtil.getSubscriptionPartitionId(correlationKey, currentPartitionCount);
    }

    public int newPartitionForCorrelationKey(final DirectBuffer correlationKey) {
      return SubscriptionUtil.getSubscriptionPartitionId(correlationKey, newPartitionCount);
    }

    @Override
    public int partitionForCorrelationKey(final DirectBuffer correlationKey) {
      final var partitionId = oldPartitionForCorrelationKey(correlationKey);
      if (completedPartitions.contains(partitionId)) {
        return newPartitionForCorrelationKey(correlationKey);
      }
      return partitionId;
    }
  }
}

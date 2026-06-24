/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import org.agrona.DirectBuffer;

public final class SubscriptionUtil {

  /**
   * Get the hash code of the subscription based on the given correlation key.
   *
   * @param correlationKey the correlation key
   * @return the hash code of the subscription
   */
  static int getSubscriptionHashCode(final DirectBuffer correlationKey) {
    return PartitionUtil.hashCode(correlationKey);
  }

  /**
   * Get the partition id for message subscription based on the given correlation key.
   *
   * @param correlationKey the correlation key
   * @param partitionCount the number of partitions
   * @return the partition id for the subscription
   */
  public static int getSubscriptionPartitionId(
      final DirectBuffer correlationKey, final int partitionCount) {
    return PartitionUtil.getPartitionId(correlationKey, partitionCount);
  }
}
